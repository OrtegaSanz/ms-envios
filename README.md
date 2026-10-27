# RutaExpress — Taller de Config Maps (proyecto base)

Proyecto base del **Taller 6: Config Maps**. Completa los `TODO` siguiendo el documento del taller.

| Dato | Valor |
|---|---|
| Microservicio | `ms-envios` · puerto `8080` |
| Configuración externa | `config/application.yml` (simula el ConfigMap montado en `/config`) |
| Manifiestos | `k8s/configmap.yaml` · `k8s/secret.yaml` · `k8s/deployment.yaml` · `k8s/service.yaml` |
| Validador | `src/main/java/com/nttdata/envios/validacion/Validador.java` |
| Endpoints | `GET /api/envios/config` · `POST /api/envios/cotizar` |

## Requisitos
- JDK 17 · IntelliJ IDEA · Postman
- **No hace falta Kubernetes**: el Validador lee los YAML como archivos.
- Conexión a internet la primera vez (descarga de dependencias).

## 1. Abre el proyecto
**File > Open…** sobre la carpeta `ms-envios` (la que contiene el `pom.xml`).

## 2. Ciclo de trabajo
```
1. Completas los TODO
2. Ejecutas el Validador  (botón ▶ sobre Validador.java)
3. Repites hasta llegar a 20/20
4. Arrancas con run-local y pruebas en Postman
```

| Criterio | Puntos |
|---|---|
| Configuración externa | 5 |
| Manifiestos Kubernetes | 6 |
| Seguridad | 4 |
| Lógica de negocio | 5 |
| **Total** | **20** |

Sin resolver nada obtienes 2/20: los valores por defecto del jar y ningún token en el código.

## Dónde están los TODO
| Archivo | TODO |
|---|---|
| `EnviosProperties.java` | 1 |
| `MsEnviosApplication.java` | 2 |
| `src/main/resources/application.yml` | 3 |
| `config/application.yml` | 4 |
| `k8s/configmap.yaml` | 5 |
| `k8s/secret.yaml` | 6 |
| `k8s/deployment.yaml` | 7 – 9 |
| `CotizadorService.java` | 10 – 14 |
| `EnviosController.java` | 15 |

## 3. Prueba en Postman
Cuando el Validador llegue a 20/20:

```
Windows:      .\run-local.ps1
Linux/macOS:  ./run-local.sh
```

El script define `COURIER_API_TOKEN` (simula el Secret) y arranca con
`--spring.config.additional-location=file:./config/` (simula el ConfigMap montado).
Luego importa `ms-envios.postman_collection.json` (**Import**, arrastra el archivo).

## Problemas frecuentes
| Problema | Solución |
|---|---|
| El Validador muestra `(archivo, clave o metodo sin completar)` | Falta un archivo YAML, una clave, o el método aún lanza `UnsupportedOperationException`. |
| `GET /config` responde `tarifaBase: 10.0` | No está leyendo `config/`: arranca con `run-local` desde la carpeta del `pom.xml`. |
| `tokenConfigurado: false` | Arrancaste sin `COURIER_API_TOKEN`. Usa `run-local`. |
| Error al parsear un YAML | Tabulaciones: YAML solo admite espacios. |
| `Port 8080 was already in use` | Detén la ejecución anterior. |
