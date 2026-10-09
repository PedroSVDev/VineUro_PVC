# Plan de Implementación: Diagnóstico y Solución Definitiva de Edición de Paneles y Teclado Táctil

Este plan presenta el diagnóstico técnico completo de la causa del fallo y las soluciones requeridas para:
1. **Solucionar la causa raíz del fallo de edición de paneles** (donde el ancho total cambiaba a 700 mm).
2. **Hacer que el teclado numérico táctil del celular aparezca automáticamente** al abrir el diálogo de edición.

---

## Diagnóstico Técnico de la Causa Raíz

### Causa del Fallo en la Edición de Paneles:
1. Al agregar una ventana de 2 paneles (ej. "2 Paneles V."), el objeto `OrderItem` se creaba por defecto con `sectionWidths = FloatArray(1) { 1500f }` (un arreglo de tamaño **1**).
2. Al cargar esa ventana en pantalla (`cargarItemActivoEnCanvas`), el arreglo `sectionWidths` de tamaño **1** sobreescribía el arreglo interno de la vista (que era de tamaño **2**).
3. Cuando el usuario pulsaba en el Panel 1 para editarlo a 700 mm, `updateSectionWidth` comprobaba el número de paneles en el arreglo (`numSections = sectionWidths.size`).
4. Como `numSections` era **1** (y no 2), la condición de autocompletado fallaba y el sistema interpretaba que se estaba modificando el ancho completo de la ventana, reduciéndola a 700 mm y mostrando dos paneles de 350 mm.

### Causa del Teclado Táctil No Visible:
- Los cuadros emergentes (`AlertDialog`) en Android no abren automáticamente el teclado en pantalla (`Soft Keyboard`) a menos que la ventana del diálogo configure explícitamente el modo `SOFT_INPUT_STATE_ALWAYS_VISIBLE`.

---

## Solución Propuesta

### 1. Corrección de Arreglos de Medidas en `OrderItem` y `WindowPreviewView.kt`
- **Inicialización correcta**: Al crear o cargar una ventana, inicializar `OrderItem.sectionWidths` y `sectionHeights` con la cantidad exacta de paneles/columnas según su diseño (ej. 2 elementos de 750 mm para 2 paneles de 1500 mm).
- **Garantía en `WindowPreviewView`**: En `updateSectionWidth` y `updateSectionHeight`, verificar que el arreglo `sectionWidths` coincida con las columnas reales del diseño (`cols`). Si no coincide, sincronizarlo antes de rebalancear.
- **Flujo de sincronización**: Eliminar sobreescrituras en `cargarItemActivoEnCanvas` que distorsionen los arreglos de secciones.

### 2. Despliegue Automático del Teclado Numérico Táctil
- En `mostrarDialogoEditarMedida`:
  1. Configurar `input.requestFocus()`.
  2. Activar `dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)`.
  3. Esto garantizará que en dispositivos móviles y emuladores aparezca inmediatamente el teclado numérico táctil en la parte inferior de la pantalla sin requerir conexiones a internet ni librerías externas.

---

## Plan de Verificación

### Pruebas Automatizadas y Compilación
- Compilación limpia con Gradle (`app:assembleDebug`).

### Verificación Manual
1. **Prueba de Edición de Panel (Ventana 2 Paneles de 1500 mm)**:
   - Crear una ventana de 2 paneles ("2 Paneles V.").
   - Pulsar el Panel 1 ("750") y cambiar su valor a `700`.
   - Verificar que el Panel 1 queda en `700 mm`, el Panel 2 en `800 mm` y el Ancho Total en `1500 mm`.
2. **Prueba de Despliegue de Teclado Táctil**:
   - Pulsar sobre cualquier medida para editar.
   - Verificar que el teclado numérico táctil del sistema se abre automáticamente enfocado en el campo de texto.
