# Atajos para compilar, testear e instalar Baul en el celular por USB.
# Requiere un JDK 17 (default: el portable bajado en ~/.jdks) y el SDK de Android
# (default: ~/Android/Sdk, mismo valor que local.properties). Se pueden sobreescribir:
#   make install JAVA_HOME=/otra/ruta/jdk-17

JAVA_HOME ?= $(HOME)/.jdks/jdk-17.0.19+10
ANDROID_HOME ?= $(HOME)/Android/Sdk
ADB := $(ANDROID_HOME)/platform-tools/adb
GRADLE := JAVA_HOME=$(JAVA_HOME) PATH="$(JAVA_HOME)/bin:$(ANDROID_HOME)/platform-tools:$$PATH" ./gradlew
PACKAGE := com.santiagojorda.baul

.PHONY: help test devices install reinstall uninstall apk apk-release

help:
	@echo "Targets disponibles:"
	@echo "  make test        - corre los unit tests (testDebugUnitTest)"
	@echo "  make devices     - lista los celulares conectados por USB"
	@echo "  make install     - compila e instala la build debug en el celular conectado"
	@echo "  make reinstall   - desinstala Baul del celular (pide confirmacion) y la vuelve a instalar"
	@echo "  make uninstall   - solo desinstala Baul del celular (pide confirmacion)"
	@echo "  make apk         - genera la APK debug (firmada con la clave de debug)"
	@echo "  make apk-release - genera la APK release (sin firmar, sin minify)"

test:
	$(GRADLE) testDebugUnitTest --console=plain

devices:
	$(ADB) devices -l

install: devices
	$(GRADLE) installDebug --console=plain

apk:
	$(GRADLE) assembleDebug --console=plain
	@echo "APK generada en app/build/outputs/apk/debug/app-debug.apk"

apk-release:
	$(GRADLE) assembleRelease --console=plain
	@echo "APK generada en app/build/outputs/apk/release/app-release-unsigned.apk (sin firmar)"

uninstall:
	@echo "Esto borra reglas, historial y cuentas de Google guardadas en la app del celular."
	@read -p "Confirmar desinstalacion de $(PACKAGE) (s/N): " ans; \
	if [ "$$ans" = "s" ] || [ "$$ans" = "S" ]; then \
		$(ADB) uninstall $(PACKAGE); \
	else \
		echo "Cancelado."; \
	fi

reinstall: uninstall install
