# Tareas de Implementación - Corrección Universal de Paneles y Teclado Táctil

- [x] 1. Crear e inicializar correctamente `OrderItem` con la cantidad exacta de secciones/paneles
  - [x] Crear función helper `createOrderItem` que calcula `cols` y `rows` del `WindowConfig` e inicializa `sectionWidths` y `sectionHeights` con la cantidad de elementos correcta (ej. 2 elementos para 2 paneles)
  - [x] Usar `createOrderItem` en `CalculoPedidoFragment.kt` (en `inicializarOrdenSugerida` y `mostrarDialogoSeleccionCatalogGeneral`)
- [x] 2. Garantizar la sincronización universal en `WindowPreviewView.kt`
  - [x] Verificar en `updateSectionWidth` y `updateSectionHeight` que si `sectionWidths.size` difiere de `cols`, se redimensione limpiamente antes de editar
  - [x] Implementar el rebalanceo entero proporcional para cualquier cantidad $X$ de paneles
- [x] 3. Despliegue automático del teclado numérico táctil en los diálogos de edición
  - [x] Configurar `input.requestFocus()` y `dialog.window?.setSoftInputMode(SOFT_INPUT_STATE_ALWAYS_VISIBLE)` en `CalculoPedidoFragment.kt`
- [x] 4. Compilar con Gradle y verificar
