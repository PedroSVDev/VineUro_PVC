# Tareas de Implementación

- [x] 1. Crear recursos y drawables para el menú y PDF
  - [x] Crear `ic_menu_hamburger.xml`
- [x] 2. Refactorizar/Extender `WindowPreviewView.kt` para renderizado en Canvas externo (PDF y vista previa)
- [x] 3. Crear el diseño del menú flotante `dialog_menu_opciones.xml` matching Capture 2
- [x] 4. Actualizar `CalculoPedidoFragment.kt` y `fragment_calculo_pedido.xml`
  - [x] Agregar el botón azul circular `btn_top_left_menu` en `fragment_calculo_pedido.xml`
  - [x] Conectar el listener del menú y la navegación a la vista previa de PDF
- [x] 5. Crear la vista personalizada y layout de la hoja PDF
  - [x] Layout `fragment_pdf_preview.xml`
  - [x] Custom View `PdfPageSheetView.kt` para renderizar hojas A4 con 6 celdas de proyectos (2x3 grid)
- [x] 6. Implementar `PdfPreviewFragment.kt`
  - [x] Lógica de paginación de proyectos (máx 6 por página, flechas de navegación si hay > 6)
  - [x] Exportación de archivo PDF físico con `android.graphics.pdf.PdfDocument` y `FileProvider`
- [x] 7. Registrar `PdfPreviewFragment` en `nav_graph.xml`
- [x] 8. Compilar y verificar cambios
