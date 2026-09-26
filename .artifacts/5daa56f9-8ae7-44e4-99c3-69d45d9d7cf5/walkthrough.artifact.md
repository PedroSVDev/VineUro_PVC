# Resumen de Ajustes y Mejoras de Renderizado PDF

Se han aplicado las optimizaciones de escala y dibujado técnico para que la vista previa y el documento PDF coincidan fielmente con el diseño de referencia.

---

## Mejoras Aplicadas

### 1. Renderizado Completo del Marco y Paneles de Ventana
- **Problema corregido**: Anteriormente, el marco de las ventanas usaba relleno blanco sin borde exterior, por lo que sobre la hoja A4 blanca se mimetizaba con el fondo y solo se apreciaban las cotas flotantes.
- **Solución**: Se integró el dibujado del borde de contorno oscuro (`frameBorderPaint`) para la estructura de la ventana y para cada uno de sus paneles de cristal. Ahora se distinguen claramente los marcos, montantes, hojas y paños sobre la hoja de papel blanca.
- **Cotas técnicas**: Las cotas de medidas ahora usan texto e indicadores en gris oscuro (`#333333`), con estética de plano arquitectónico/técnico.

### 2. Formateo del Encabezado (Anti-solapamiento)
- **Problema corregido**: Los datos del cliente (nombre largo con DNI y dirección) se encimaban verticalmente en la esquina superior derecha.
- **Solución**: Se implementó una función auxiliar de truncado con elipsis (`drawTruncatedText`) y un espaciado entre líneas calibrado (`lineH = 12.5dp`). Todos los campos (`CLIENTE`, `dirección`, `teléfono`, `correo`, `ID de oferta`) aparecen alineados y ordenados sin ningún tipo de solapamiento.
- **Logo central**: Actualizado con marco dorado y fondo blanco en el centro del encabezado, idéntico a la captura 2.

### 3. Escala y Ancho de Hoja en Pantalla
- **Problema corregido**: La hoja A4 aparecía reducida en el centro con amplios márgenes grises laterales.
- **Solución**: Se ajustó el margen contenedor en `fragment_pdf_preview.xml` a `2dp`, permitiendo que la hoja aproveche casi todo el ancho de la pantalla del dispositivo. Esto incrementa considerablemente el tamaño y legibilidad de los dibujos de ventanas dentro de cada una de las celdas 2x3.

---

## Verificación
- Compilación de Gradle (`app:assembleDebug`) finalizada exitosamente.
