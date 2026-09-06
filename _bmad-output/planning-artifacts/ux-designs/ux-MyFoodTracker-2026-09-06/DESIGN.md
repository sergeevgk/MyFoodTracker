---
name: MyFoodTracker
description: Local-first, privacy-first nutrition and water tracker. Clean, fast, Material Design 3 aligned.
status: final
created: 2026-09-06
updated: 2026-09-06
colors:
  surface-base: '#F8F9FA'
  surface-raised: '#FFFFFF'
  ink-primary: '#121417'
  ink-secondary: '#5A6065'
  ink-disabled: '#9EA5AC'
  accent-primary: '#1B4D3E'
  accent-secondary: '#2D6A4F'
  accent-highlight: '#E9C46A'
  water-primary: '#0288D1'
  border-hairline: '#E2E8F0'
  surface-base-dark: '#121417'
  surface-raised-dark: '#1A1C1E'
  ink-primary-dark: '#F1F3F5'
  ink-secondary-dark: '#9EA5AC'
  ink-disabled-dark: '#5A6065'
  accent-primary-dark: '#2D6A4F'
  accent-secondary-dark: '#52B788'
  accent-highlight-dark: '#F4A261'
  water-primary-dark: '#29B6F6'
  border-hairline-dark: '#2C3036'
typography:
  headline:
    note: 'Android Headline Small / Medium · Tabular figures for macro numbers'
  body:
    note: 'Android Body Large / Medium'
  caption:
    note: 'Android Label Small / Body Small'
rounded:
  sm: 8px
  md: 12px
  lg: 16px
  xl: 28px
spacing:
  '1': 4px
  '2': 8px
  '3': 12px
  '4': 16px
  '5': 24px
  '6': 32px
---

# DESIGN.md: MyFoodTracker Visual Identity & Token Specifications

## Brand & Style

MyFoodTracker is designed around utility, instant performance, and user sovereignty. Unlike modern diet tracking applications that pollute screens with ad banners, gamified streaks, and paywall overlays, MyFoodTracker provides a clean, calm, and highly responsive interface for health-conscious users.

The visual style embraces Material Design 3 principles with an organic, healthy color palette (Bio-Green `#1B4D3E` and Ocean Blue `#0288D1`). Surfaces use subtle contrast between canvas (`#F8F9FA`) and raised container cards (`#FFFFFF`). Numeric macro values use monospace/tabular figures so digits do not jump during rapid UI state updates.

## Colors

The color system is calibrated for high legibility, clean data visualization, and seamless dark mode support.

- **Accent Primary (`#1B4D3E` light / `#2D6A4F` dark)**: Deep Emerald Green. Represents health and natural nutrition. Used for primary FABs, active tab indicators, and progress gauges.
- **Accent Highlight (`#E9C46A` light / `#F4A261` dark)**: Warm Amber/Gold. Used for carbohydrate and energy indicators.
- **Water Primary (`#0288D1` light / `#29B6F6` dark)**: Crisp Hydration Blue. Dedicated exclusively to water intake widgets, quick `+250ml` FABs, and water progress rings.
- **Canvas Base (`#F8F9FA` light / `#121417` dark)**: Main background color.
- **Container Raised (`#FFFFFF` light / `#1A1C1E` dark)**: Card surface background for meal slots, food search sheets, and dialogs.
- **Border Hairline (`#E2E8F0` light / `#2C3036` dark)**: Subtle 1dp boundaries between list items and section cards.

Avoid: Loud red error banners for empty states, flashy gradients, ad placeholders, or unnecessary chromatic badges.

## Typography

Text hierarchy follows Material Design 3 type scales using system Roboto/Inter fonts.

- **Headline Small (`24sp`, Medium)**: Used for daily total calorie headers and main screen titles.
- **Title Medium (`16sp`, SemiBold)**: Used for meal slot headers (`Breakfast`, `Lunch`, `Dinner`) and food item titles.
- **Body Large (`16sp`, Regular)**: Main text for input fields, search autocomplete query text, and settings labels.
- **Body Small / Label (`12sp`, Medium, Monospace)**: Used for numerical macro readouts (`Calories: 450 kcal`, `P: 30g | C: 45g | F: 12g`). Requires `fontFeatureSettings = "tnum"` (tabular figures) so digits align vertically.

## Layout & Spacing

Spacing uses a standardized 4dp grid system (`4dp`, `8dp`, `12dp`, `16dp`, `24dp`, `32dp`).

- **Screen Margins**: `16dp` padding on phone surfaces; `24dp` on tablet/foldable surfaces.
- **Card Spacing**: `12dp` vertical gap between meal slot cards on the dashboard.
- **Horizontal Week Strip**: `8dp` horizontal padding between day pill indicators.
- **Modal Bottom Sheet**: Max height `85vh` with `16dp` internal padding.

## Elevation & Depth

MyFoodTracker favors tonal separation over heavy drop shadows to preserve offline UI rendering performance.

- **Level 0 (Flat)**: Surface canvas and background items sit at `0dp` elevation.
- **Level 1 (Card Container)**: Meal slot cards and progress cards sit at `1dp` tonal elevation (`surface-raised`).
- **Level 2 (Floating Action Buttons / Sheets)**: Search bottom sheet and quick-add FAB sit at `3dp` elevation with subtle soft drop shadow.

## Shapes

- **`rounded/sm` (8px)**: Input text fields, individual list item selection rows, and search chips.
- **`rounded/md` (12px)**: Meal slot cards, food detail dialogs, and macro progress cards.
- **`rounded/lg` (16px)**: Search bottom sheet container corners.
- **`rounded/xl` (28px)**: Floating Action Buttons (FAB) and date pill indicators.

## Components

### 1. Macro Progress Header Card
- **Surface**: `surface-raised` with `rounded/md` corners and `1dp` hairline border.
- **Layout**: 4 circular/linear progress bars showing Calories (Primary Green), Protein (Deep Teal), Carbs (Amber), and Fats (Coral).
- **Behavior**: Animates smoothly upon database update; updates in `< 50ms`.

### 2. Water Quick-Log Container Widget
- **Surface**: Light blue tint background (`#E1F5FE` light / `#002F4B` dark) with `rounded/md` shape.
- **Control**: Prominent `+250ml` button in `water-primary` blue with white text/icon.
- **Feedback**: Instant circular ring animation filling up to the daily target.

### 3. Food Search & Autocomplete Sheet
- **Surface**: Bottom Sheet (`rounded/lg` top corners) in `surface-raised`.
- **Search Bar**: Full-width input field with clear (`X`) button and instant prefix typeahead.
- **List Items**: Two-line list items (Food Name top, Brand + Base Macros bottom) separated by hairline dividers. Custom foods feature a small "Custom" chip tag.

### 4. Horizontal Week Navigation Strip
- **Layout**: Scrollable horizontal row of 7 day pills (`Mon`, `Tue`, `Wed`...).
- **Selected Day**: Filled pill (`accent-primary`) with white text.
- **Unselected Day**: Transparent background with `ink-secondary` text.

## Do's and Don'ts

| Do | Don't |
|---|---|
| Use tabular monospace figures (`tnum`) for macro numbers | Use proportional fonts where digits jump when updating |
| Use tonal elevation and 1dp hairline borders | Use heavy 90s drop shadows or complex multi-stop gradients |
| Provide instant tactile feedback for `+250ml` water tap | Show loading spinners for local SQLite DB operations |
| Allow skipping macro/water targets during profile setup | Require users to enter all 4 macro goals if they only want calorie tracking |
| Display explicit "Custom" badge on user-created foods | Mix custom foods and pre-populated foods without distinction |
