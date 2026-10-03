# Tareas de Implementación - Selección Táctil de Cotas y Rebalanceo

- [x] 1. Mejorar el selector táctil de etiquetas en `WindowPreviewView.kt`
  - [x] Implementar la búsqueda del distintivo/cota (`BadgeArea`) por distancia euclidiana mínima a la posición de toque `(x, y)`
- [x] 2. Reforzar la lógica matemática en `WindowPreviewView.kt`
  - [x] Actualizar `updateSectionWidth` para mantener el ancho total estricto si `newWidthMm < totalWidth`
  - [x] Expandir el ancho total automáticamente si `newWidthMm >= totalWidth`
  - [x] Aplicar la misma lógica a `updateSectionHeight`
- [x] 3. Mejorar la claridad de información en los diálogos de `CalculoPedidoFragment.kt`
  - [x] Incluir el valor total actual en el título/mensaje del diálogo de edición
  - [x] Mostrar un Toast detallado con los valores autocompletados de los demás paneles
- [x] 4. Compilar con Gradle y verificar los cambios
