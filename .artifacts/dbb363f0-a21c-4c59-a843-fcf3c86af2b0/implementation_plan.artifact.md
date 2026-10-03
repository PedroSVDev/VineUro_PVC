# Implementation Plan - Refine Selection Icons & Black Cell Highlighting

Refine the 6 custom selection drawables and cell styling in `PreciosFragment.kt` based on user feedback:
1. **Unselected State**: Make the background transparent so it blends naturally with the row background (instead of an opaque white square), retaining clean dark borders and shape markers.
2. **Selected State**: When any of the 6 options is selected (`pc`, `m2`, `m`, etc.), its cell background turns solid black (`#000000`), and the inner icon renders in **white** (white borders and white inner shapes/markers) so it is clearly visible against the black background.
3. **Mutual Exclusivity**: Ensure only one cell in the row can be selected (and thus black) at any given time.

## User Review Required

> [!IMPORTANT]
> - All 6 selector drawables (`bg_sel_option_0.xml` through `bg_sel_option_5.xml`) will be updated:
>   - **Unselected**: Transparent background with black outlines/markers.
>   - **Selected**: Solid black background with white outlines/markers.

## Proposed Changes

### UI & Drawables

#### [MODIFY] [bg_sel_option_0.xml](file:///C:/Users/ACER/OneDrive/Documentos/Android%20Proyect%20Capstone/app/src/main/res/drawable/bg_sel_option_0.xml) through [bg_sel_option_5.xml](file:///C:/Users/ACER/OneDrive/Documentos/Android%20Proyect%20Capstone/app/src/main/res/drawable/bg_sel_option_5.xml)
- Update all 6 selectors to have transparent/row-matching unselected backgrounds with black borders, and solid black selected backgrounds with white inner icons.

## Verification Plan

### Automated Tests
- Run `gradle_build("app:assembleDebug")` to ensure successful compilation.

### Manual Verification
- Test selecting each of the 6 options in the employment/extras table to verify that the selected cell turns solid black with white icons, unselected cells blend naturally with the row, and mutual exclusivity works perfectly.
