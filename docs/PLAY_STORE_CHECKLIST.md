# Checklist de publicación en Play Store

## Seguridad y configuración
- [ ] Definir MAPS_API_KEY en local.properties (o variable de entorno) y restringirla por package + SHA-1.
- [ ] Desplegar reglas de Firestore desde [firestore.rules](../firestore.rules).
- [ ] Revisar que no haya secretos en el repositorio.

## Build y firma
- [ ] Confirmar applicationId final (no usar com.example.*) en app/build.gradle.kts.
- [ ] Incrementar versionCode y versionName.
- [ ] Generar keystore de release y configurar signingConfig en Gradle.
- [ ] Compilar AAB release (bundleRelease) y probar en dispositivo.

## Play Console
- [ ] Completar Data Safety (cámara, micrófono, ubicación, Firebase Auth/Firestore).
- [ ] Agregar URL de política de privacidad.
- [ ] Subir capturas, ícono, banner, y ficha de la app.
- [ ] Crear lanzamiento (producción o prueba) y subir AAB.

## Verificaciones finales
- [ ] Probar flujo de registro, login, mapas y permisos.
- [ ] Verificar que la app funcione sin logs sensibles en release.
- [ ] Revisar permisos declarados en Manifest.
