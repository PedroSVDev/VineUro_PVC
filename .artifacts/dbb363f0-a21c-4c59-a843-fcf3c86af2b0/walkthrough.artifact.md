# Walkthrough - Refined Selection Drawables (Transparent Unselected & White Icons on Black Selected)

Refined the 6 custom shape selectors (`bg_sel_option_0.xml` through `bg_sel_option_5.xml`) so unselected items blend transparently into the row background, and selected items turn their cell background solid black with crisp white icons/markers.

## Changes

### UI & Drawables

#### [bg_sel_option_0.xml](file:///C:/Users/ACER/OneDrive/Documentos/Android%20Proyect%20Capstone/app/src/main/res/drawable/bg_sel_option_0.xml) through [bg_sel_option_5.xml](file:///C:/Users/ACER/OneDrive/Documentos/Android%20Proyect%20Capstone/app/src/main/res/drawable/bg_sel_option_5.xml)

- **Unselected state**: Transparent background (`@android:color/transparent`), black borders (`#000000`), and dark inner shapes/markers.
- **Selected state**: Solid black background (`#000000`), with clean **white** icons and inner markers (`#FFFFFF`) ensuring high contrast and visibility against the black cell background.

## Verification Results

### Automated Tests
- Ran `gradle_build("app:assembleDebug")` successfully with no compilation errors.
