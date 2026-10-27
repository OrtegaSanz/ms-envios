package com.nttdata.envios.validacion;

import com.nttdata.envios.CotizadorService;
import com.nttdata.envios.EnviosController;
import com.nttdata.envios.EnviosProperties;
import com.nttdata.envios.MsEnviosApplication;
import com.nttdata.envios.model.ConfigResponse;
import com.nttdata.envios.model.CotizacionRequest;
import com.nttdata.envios.model.CotizacionResponse;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.yaml.snakeyaml.Yaml;

import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Verificaciones automaticas del taller: 20 puntos. (Resuelto: no modificar)
 *
 * Comprueba cuatro cosas:
 *   A. Que la configuracion externa (config/application.yml) exista y sea la del caso.
 *   B. Que los manifiestos de Kubernetes (k8s/) esten bien escritos y conectados.
 *   C. Que el token del courier no quede en ningun archivo que no sea el Secret.
 *   D. Que la logica de cotizacion cumpla las reglas de negocio.
 *
 * Ejecutalo con el boton Play. No hace falta arrancar la aplicacion ni tener Kubernetes:
 * los YAML se leen como archivos y la logica se prueba instanciando las clases.
 */
public class Validador {

    private static final String TOKEN = "tk-courier-9f3a-2026";

    private static final String CONFIG_EXTERNA = "config/application.yml";
    private static final String CONFIG_JAR = "src/main/resources/application.yml";
    private static final String K8S_CONFIGMAP = "k8s/configmap.yaml";
    private static final String K8S_SECRET = "k8s/secret.yaml";
    private static final String K8S_DEPLOYMENT = "k8s/deployment.yaml";

    private static final String NOMBRE_CONFIGMAP = "ms-envios-config";
    private static final String NOMBRE_SECRET = "ms-envios-secret";
    private static final String CLAVE_SECRET = "api-token";

    /** Las 5 claves del caso y sus valores (seccion 5.2 del enunciado). */
    private static final String[] CLAVES = {"tarifa-base", "costo-por-kg", "recargo-express", "envio-gratis-desde", "peso-maximo-kg"};
    private static final double[] VALORES_CASO = {8.00, 2.50, 0.30, 150.00, 30};
    private static final double[] VALORES_JAR = {10.00, 3.00, 0.25, 200.00, 25};

    private static final Map<String, int[]> RESUMEN = new LinkedHashMap<>();
    private static int numero = 0;

    @FunctionalInterface
    interface Prueba {
        boolean ejecutar() throws Exception;
    }

    public static void main(String[] args) {
        System.out.println("RutaExpress - Validacion automatica del taller Config Maps");
        System.out.println("==========================================================");

        // ================= A. Configuracion externa (5) =================
        verificar("Configuracion externa", "config/application.yml define el bloque envios con sus 5 claves",
                () -> tieneLasCincoClaves(envios(yaml(CONFIG_EXTERNA))));

        verificar("Configuracion externa", "config/application.yml tiene los valores del caso (8.00 / 2.50 / 0.30 / 150 / 30)",
                () -> coinciden(envios(yaml(CONFIG_EXTERNA)), VALORES_CASO));

        verificar("Configuracion externa", "El application.yml del jar conserva los valores por defecto",
                () -> {
                    Map<String, Object> e = envios(yaml(CONFIG_JAR));
                    return coinciden(e, VALORES_JAR) && "PEN".equals(String.valueOf(valor(e, "moneda")));
                });

        verificar("Configuracion externa", "EnviosProperties tiene @ConfigurationProperties(prefix = \"envios\")",
                () -> {
                    ConfigurationProperties cp = EnviosProperties.class.getAnnotation(ConfigurationProperties.class);
                    return cp != null && ("envios".equals(cp.prefix()) || "envios".equals(cp.value()));
                });

        verificar("Configuracion externa", "La aplicacion habilita EnviosProperties con @EnableConfigurationProperties",
                () -> {
                    EnableConfigurationProperties e = MsEnviosApplication.class.getAnnotation(EnableConfigurationProperties.class);
                    return e != null && Arrays.asList(e.value()).contains(EnviosProperties.class);
                });

        // ================= B. Manifiestos Kubernetes (6) =================
        verificar("Manifiestos Kubernetes", "configmap.yaml: ConfigMap " + NOMBRE_CONFIGMAP + " con la clave application.yml",
                () -> {
                    Map<String, Object> cm = yaml(K8S_CONFIGMAP);
                    return cm != null && "ConfigMap".equals(cm.get("kind"))
                            && NOMBRE_CONFIGMAP.equals(nav(cm, "metadata", "name"))
                            && nav(cm, "data", "application.yml") instanceof String;
                });

        verificar("Manifiestos Kubernetes", "El contenido del ConfigMap coincide con config/application.yml",
                () -> {
                    Map<String, Object> cm = yaml(K8S_CONFIGMAP);
                    Object interno = nav(cm, "data", "application.yml");
                    if (!(interno instanceof String s)) return false;
                    Map<String, Object> dentro = envios(cargar(s));
                    return coinciden(dentro, VALORES_CASO) && coinciden(envios(yaml(CONFIG_EXTERNA)), VALORES_CASO);
                });

        verificar("Manifiestos Kubernetes", "secret.yaml: Secret " + NOMBRE_SECRET + " con la clave " + CLAVE_SECRET,
                () -> {
                    Map<String, Object> sec = yaml(K8S_SECRET);
                    if (sec == null || !"Secret".equals(sec.get("kind"))) return false;
                    if (!NOMBRE_SECRET.equals(nav(sec, "metadata", "name"))) return false;
                    Object plano = nav(sec, "stringData", CLAVE_SECRET);
                    if (plano != null) return TOKEN.equals(String.valueOf(plano).trim());
                    Object b64 = nav(sec, "data", CLAVE_SECRET);
                    if (b64 == null) return false;
                    String dec = new String(Base64.getDecoder().decode(String.valueOf(b64).trim()), StandardCharsets.UTF_8);
                    return TOKEN.equals(dec.trim());
                });

        verificar("Manifiestos Kubernetes", "deployment.yaml monta el ConfigMap en /config (sin subPath)",
                () -> {
                    Map<String, Object> c = contenedor();
                    String vol = volumenDelConfigMap();
                    if (c == null || vol == null) return false;
                    for (Map<String, Object> m : lista(c.get("volumeMounts"))) {
                        String ruta = String.valueOf(m.get("mountPath"));
                        if (vol.equals(m.get("name")) && (ruta.equals("/config") || ruta.equals("/config/")))
                            return m.get("subPath") == null;
                    }
                    return false;
                });

        verificar("Manifiestos Kubernetes", "deployment.yaml define SPRING_CONFIG_ADDITIONAL_LOCATION = file:/config/",
                () -> {
                    Map<String, Object> v = variable("SPRING_CONFIG_ADDITIONAL_LOCATION");
                    if (v == null) return false;
                    String valor = String.valueOf(v.get("value"));
                    return valor.equals("file:/config/") || valor.equals("optional:file:/config/");
                });

        verificar("Manifiestos Kubernetes", "COURIER_API_TOKEN llega desde el Secret con secretKeyRef",
                () -> {
                    Map<String, Object> v = variable("COURIER_API_TOKEN");
                    if (v == null || v.get("value") != null) return false;
                    return NOMBRE_SECRET.equals(nav(v, "valueFrom", "secretKeyRef", "name"))
                            && CLAVE_SECRET.equals(nav(v, "valueFrom", "secretKeyRef", "key"));
                });

        // ================= C. Seguridad (4) =================
        verificar("Seguridad", "El application.yml del jar no contiene el token (RN-06)",
                () -> {
                    String s = texto(CONFIG_JAR);
                    return s != null && !s.contains(TOKEN);
                });

        verificar("Seguridad", "Ni config/ ni el ConfigMap contienen el token: es un dato del Secret",
                () -> {
                    String ext = texto(CONFIG_EXTERNA), cm = texto(K8S_CONFIGMAP);
                    return tieneLasCincoClaves(envios(yaml(CONFIG_EXTERNA)))
                            && yaml(K8S_CONFIGMAP) != null
                            && !ext.contains(TOKEN) && !cm.contains(TOKEN);
                });

        verificar("Seguridad", "Ningun archivo .java guarda el token",
                () -> {
                    List<String> malos = fuentesConToken();
                    if (!malos.isEmpty()) System.out.println("          archivos: " + malos);
                    return malos.isEmpty();
                });

        verificar("Seguridad", "GET /config informa si hay token pero nunca lo devuelve (RN-06)",
                () -> {
                    EnviosController c = controlador(TOKEN);
                    ConfigResponse r = c.config();
                    EnviosController sin = controlador("");
                    return r != null && r.tokenConfigurado() && !String.valueOf(r).contains(TOKEN)
                            && r.tarifaBase() == 8.00 && !sin.config().tokenConfigurado();
                });

        // ================= D. Logica de negocio (5) =================
        verificar("Logica de negocio", "RN-01: 4 kg, compra 100 -> 18.00",
                () -> {
                    CotizacionResponse r = servicio().cotizar(new CotizacionRequest(4, 100, false));
                    return r.costo() == 18.00 && !r.gratis() && "PEN".equals(r.moneda());
                });

        verificar("Logica de negocio", "RN-02: express suma el 30 % -> 23.40",
                () -> servicio().cotizar(new CotizacionRequest(4, 100, true)).costo() == 23.40);

        verificar("Logica de negocio", "RN-03: desde 150 el envio normal es gratis; el express no",
                () -> {
                    CotizacionResponse normal = servicio().cotizar(new CotizacionRequest(4, 150, false));
                    CotizacionResponse express = servicio().cotizar(new CotizacionRequest(4, 150, true));
                    return normal.costo() == 0.00 && normal.gratis()
                            && express.costo() == 23.40 && !express.gratis();
                });

        verificar("Logica de negocio", "RN-04: peso 0 o mayor al maximo -> 400; 28 kg -> 200 (78.00)",
                () -> {
                    EnviosController c = controlador(TOKEN);
                    var cero = c.cotizar(new CotizacionRequest(0, 50, false));
                    var exceso = c.cotizar(new CotizacionRequest(31, 50, false));
                    var limite = c.cotizar(new CotizacionRequest(28, 50, false));
                    return estado(cero) == 400 && estado(exceso) == 400
                            && estado(limite) == 200 && ((CotizacionResponse) cuerpo(limite)).costo() == 78.00;
                });

        verificar("Logica de negocio", "RN-05: redondeo a 2 decimales (1.37 kg express -> 14.85)",
                () -> servicio().cotizar(new CotizacionRequest(1.37, 10, true)).costo() == 14.85
                        && servicio().cotizar(new CotizacionRequest(2.2, 10, false)).costo() == 13.50);

        // ================= Resumen =================
        System.out.println();
        System.out.println("Resumen por criterio");
        System.out.println("--------------------");
        int total = 0;
        for (Map.Entry<String, int[]> e : RESUMEN.entrySet()) {
            System.out.printf("%-26s %d / %d%n", e.getKey(), e.getValue()[0], e.getValue()[1]);
            total += e.getValue()[0];
        }
        System.out.println("--------------------");
        System.out.printf("PUNTAJE AUTOMATICO: %d / 20%n", total);
    }

    // ------------------------- utilidades -------------------------

    /** Propiedades del caso, como las veria el pod con el ConfigMap montado. */
    private static EnviosProperties propsDelCaso() {
        return new EnviosProperties("PEN", 8.00, 2.50, 0.30, 150.00, 30);
    }

    private static CotizadorService servicio() {
        return new CotizadorService(propsDelCaso());
    }

    private static EnviosController controlador(String token) throws Exception {
        EnviosProperties p = propsDelCaso();
        EnviosController c = new EnviosController(new CotizadorService(p), p);
        Field f = EnviosController.class.getDeclaredField("apiToken");
        f.setAccessible(true);
        f.set(c, token);
        return c;
    }

    private static int estado(Object respuesta) throws Exception {
        Object codigo = respuesta.getClass().getMethod("getStatusCode").invoke(respuesta);
        return (int) codigo.getClass().getMethod("value").invoke(codigo);
    }

    private static Object cuerpo(Object respuesta) throws Exception {
        return respuesta.getClass().getMethod("getBody").invoke(respuesta);
    }

    private static String texto(String ruta) {
        try {
            return Files.readString(Paths.get(ruta));
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cargar(String contenido) {
        Object o = new Yaml().load(contenido);
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> yaml(String ruta) {
        Path p = Paths.get(ruta);
        if (!Files.exists(p)) return null;
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            Object o = new Yaml().load(r);
            return o instanceof Map ? (Map<String, Object>) o : null;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object nav(Map<String, Object> m, String... ruta) {
        Object actual = m;
        for (String k : ruta) {
            if (!(actual instanceof Map)) return null;
            actual = ((Map<String, Object>) actual).get(k);
        }
        return actual;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> envios(Map<String, Object> raiz) {
        Object e = raiz == null ? null : raiz.get("envios");
        return e instanceof Map ? (Map<String, Object>) e : null;
    }

    /** Acepta la clave en kebab-case (tarifa-base) o en camelCase (tarifaBase), como Spring. */
    private static Object valor(Map<String, Object> envios, String kebab) {
        if (envios == null) return null;
        if (envios.containsKey(kebab)) return envios.get(kebab);
        StringBuilder camel = new StringBuilder();
        boolean mayus = false;
        for (char ch : kebab.toCharArray()) {
            if (ch == '-') { mayus = true; continue; }
            camel.append(mayus ? Character.toUpperCase(ch) : ch);
            mayus = false;
        }
        return envios.get(camel.toString());
    }

    private static boolean tieneLasCincoClaves(Map<String, Object> envios) {
        if (envios == null) return false;
        for (String k : CLAVES) if (valor(envios, k) == null) return false;
        return true;
    }

    private static boolean coinciden(Map<String, Object> envios, double[] esperados) {
        if (!tieneLasCincoClaves(envios)) return false;
        for (int i = 0; i < CLAVES.length; i++) {
            Object v = valor(envios, CLAVES[i]);
            double d;
            try {
                d = v instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(v).trim());
            } catch (NumberFormatException e) {
                return false;
            }
            if (Math.abs(d - esperados[i]) > 0.0001) return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lista(Object o) {
        List<Map<String, Object>> r = new ArrayList<>();
        if (o instanceof List<?> l) for (Object x : l) if (x instanceof Map) r.add((Map<String, Object>) x);
        return r;
    }

    private static Map<String, Object> contenedor() {
        List<Map<String, Object>> cs = lista(nav(yaml(K8S_DEPLOYMENT), "spec", "template", "spec", "containers"));
        return cs.isEmpty() ? null : cs.get(0);
    }

    private static String volumenDelConfigMap() {
        for (Map<String, Object> v : lista(nav(yaml(K8S_DEPLOYMENT), "spec", "template", "spec", "volumes"))) {
            if (NOMBRE_CONFIGMAP.equals(nav(v, "configMap", "name"))) return String.valueOf(v.get("name"));
        }
        return null;
    }

    private static Map<String, Object> variable(String nombre) {
        Map<String, Object> c = contenedor();
        if (c == null) return null;
        for (Map<String, Object> e : lista(c.get("env"))) if (nombre.equals(e.get("name"))) return e;
        return null;
    }

    private static List<String> fuentesConToken() throws Exception {
        List<String> malos = new ArrayList<>();
        try (Stream<Path> s = Files.walk(Paths.get("src/main/java"))) {
            for (Path p : s.filter(x -> x.toString().endsWith(".java")).toList()) {
                if (p.toString().contains("validacion")) continue;   // este archivo lo declara a proposito
                if (Files.readString(p).contains(TOKEN)) malos.add(p.getFileName().toString());
            }
        }
        return malos;
    }

    private static void verificar(String criterio, String descripcion, Prueba prueba) {
        numero++;
        boolean ok;
        String detalle = "";
        try {
            ok = prueba.ejecutar();
        } catch (UnsupportedOperationException e) {
            ok = false;
            detalle = "  (pendiente: " + e.getMessage() + ")";
        } catch (NullPointerException e) {
            ok = false;
            detalle = "  (archivo, clave o metodo sin completar)";
        } catch (Throwable e) {
            ok = false;
            detalle = "  (" + e.getClass().getSimpleName() + ")";
        }
        int[] r = RESUMEN.computeIfAbsent(criterio, k -> new int[2]);
        r[1]++;
        if (ok) r[0]++;
        System.out.printf("%s %2d. %s%s%n", ok ? "[OK]" : "[X] ", numero, descripcion, detalle);
    }
}
