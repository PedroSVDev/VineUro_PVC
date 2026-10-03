# Plan de Implementación: Ajuste Preciso de Selección Táctil de Cotas y Rebalanceo de Medidas

Este plan resuelve de forma definitiva el problema reportado, donde la edición de la medida de un panel afectaba el ancho total de la ventana:

1. **Mejora en la Detección Táctil de las Etiquetas de Medidas (`onTouchEvent`)**:
   - Para evitar que al pulsar la cota de un panel (ej. 750 mm) el toque active por error la cota del Ancho Total (1500 mm) por cercanía táctil, se implementará un selector por **distancia euclidiana mínima al centro del toque**.
   - Se mostrará el contexto en el diálogo: *"Editar Ancho Panel 1 (Ancho total actual: 1500 mm)"*.

2. **Lógica Completa de Rebalanceo e Incremento Dinámico (`updateSectionWidth`)**:
   - **Caso A (La medida del panel es menor que el Ancho Total, ej. poner 700 mm en ventana de 1500 mm)**:
     - El Ancho Total se mantiene **estrictamente fijo en 1500 mm**.
     - El Panel editado se fija en **700 mm**.
     - El saldo restante ($1500 - 700 = 800\text{ mm}$) se asigna al otro panel (o se reparte de forma entera si hay 3 o más paneles).
   - **Caso B (La medida del panel es mayor o igual al Ancho Total, ej. poner 1600 mm en ventana de 1500 mm)**:
     - El Ancho Total se expande automáticamente a $1600 + \text{otros paneles}$ (ej. $1600 + 750 = 2350\text{ mm}$), permitiendo al usuario agrandar un panel sin romper la estructura.

---

## Cambios Propuestos

### 1. Mejorar el Algoritmo de Toque en `WindowPreviewView.kt`
- En `onTouchEvent`:
  - Buscar la etiqueta de cota (`BadgeArea`) que esté más cercana al punto de toque $(x, y)$ del dedo del usuario.
  - Esto evita la confusión táctil entre la línea de paneles y la línea del ancho total.

### 2. Reforzar la Lógica Matemática en `WindowPreviewView.kt`
- En `updateSectionWidth` y `updateSectionHeight`:
  - Si `newWidthMm < oldTotalInt`:
    Mantener $W_{total}$ exacto. Asignar $W_{panel} = newWidthMm$. Repartir $W_{total} - newWidthMm$ de forma entera entre las demás secciones.
  - Si `newWidthMm >= oldTotalInt`:
    Crecer el total de la ventana a $newWidthMm + \sum W_{otros}$, asegurando que ningún panel quede con menos del mínimo técnico (50 mm).

### 3. Claridad en el Diálogo en `CalculoPedidoFragment.kt`
- Al abrir el diálogo de edición:
  - Título: *"Editar Ancho Panel 1 (mm)"*
  - Mensaje/Aviso: *"Ancho total de la ventana: 1500 mm"*
  - Toast de confirmación claro: *"Panel 1: 700 mm (Panel 2: 800 mm | Total: 1500 mm)"*.

---

## Plan de Verificación

### Pruebas Automatizadas y Compilación
- Compilación limpia con Gradle (`app:assembleDebug`).

### Verificación Manual
1. **Edición de Panel en Ventana de 1500 mm (2 paneles de 750 mm)**:
   - Pulsar directamente sobre la etiqueta "750" del Panel 1.
   - Ingresar `700` y presionar APLICAR.
   - Verificar que el Panel 1 cambia a `700 mm`, el Panel 2 cambia a `800 mm` y el Ancho Total se mantiene **estrictamente en 1500 mm**.
2. **Edición con Ampliación de Ventana**:
   - En la misma ventana de 1500 mm, editar el Panel 1 e ingresar `1600`.
   - Verificar que el Panel 1 cambia a `1600 mm`, el Panel 2 se mantiene en `800 mm` y el Ancho Total crece a `2400 mm`.
