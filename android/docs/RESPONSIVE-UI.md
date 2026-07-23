# Responsive UI (Views / Material 3 patterns)

> This app is **XML + Java Views**, not Jetpack Compose. The same goals as Compose
> (`WindowInsets.safeDrawing`, `fillMaxWidth`, `weight`, `aspectRatio`, window size classes)
> are implemented with the View equivalents below.

## Mapping

| Compose idea | View equivalent in this project |
|--------------|----------------------------------|
| `WindowInsets.safeDrawing` | `ResponsiveLayoutHelper.safeDrawingTypeMask()` + `DesignInsetsHelper` / `Tools.setNavigation` |
| `statusBarsPadding` / `navigationBarsPadding` | Top/bottom chrome margins from insets |
| `Scaffold` | Activity root + top actions + bottom chrome |
| `BoxWithConstraints` / size classes | `ResponsiveLayoutHelper.widthSizeClass()` |
| `fillMaxWidth` / `weight` | `match_parent` / `0dp` + `layout_weight` |
| `aspectRatio` | ConstraintLayout `layout_constraintDimensionRatio` |

## Resource qualifiers

- `values/dimens.xml` — phone defaults
- `values-sw600dp/dimens.xml` — tablets / large phones
- `values-sw720dp/dimens.xml` — large tablets
- `values-land/dimens.xml` — landscape

## Key classes

- `utils/ResponsiveLayoutHelper.java` — size classes, adaptive grid spans, safe drawing helpers
- `utils/DesignInsetsHelper.java` — edge-to-edge + chrome insets for V2/V3
- `utils/Tools.setNavigation` / `setWindowInsetsLayout` — classic screens
