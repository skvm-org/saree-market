---
name: Saree Market Wholesale Live
colors:
  surface: '#f8f9ff'
  surface-dim: '#cbdbf5'
  surface-bright: '#f8f9ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#eff4ff'
  surface-container: '#e5eeff'
  surface-container-high: '#dce9ff'
  surface-container-highest: '#d3e4fe'
  on-surface: '#0b1c30'
  on-surface-variant: '#564145'
  inverse-surface: '#213145'
  inverse-on-surface: '#eaf1ff'
  outline: '#897175'
  outline-variant: '#dcbfc3'
  surface-tint: '#a63455'
  primary: '#6c002b'
  on-primary: '#ffffff'
  primary-container: '#8b1e41'
  on-primary-container: '#ff9db2'
  inverse-primary: '#ffb1c0'
  secondary: '#006d2f'
  on-secondary: '#ffffff'
  secondary-container: '#5dfd8a'
  on-secondary-container: '#007232'
  tertiary: '#003c16'
  on-tertiary: '#ffffff'
  tertiary-container: '#005622'
  on-tertiary-container: '#53d171'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#ffd9df'
  primary-fixed-dim: '#ffb1c0'
  on-primary-fixed: '#3f0016'
  on-primary-fixed-variant: '#871a3e'
  secondary-fixed: '#66ff8e'
  secondary-fixed-dim: '#3de273'
  on-secondary-fixed: '#002109'
  on-secondary-fixed-variant: '#005322'
  tertiary-fixed: '#7ffc97'
  tertiary-fixed-dim: '#62df7d'
  on-tertiary-fixed: '#002109'
  on-tertiary-fixed-variant: '#005320'
  background: '#f8f9ff'
  on-background: '#0b1c30'
  surface-variant: '#d3e4fe'
typography:
  display-lg:
    fontFamily: Bricolage Grotesque
    fontSize: 40px
    fontWeight: '700'
    lineHeight: 48px
    letterSpacing: -0.02em
  display-lg-mobile:
    fontFamily: Bricolage Grotesque
    fontSize: 30px
    fontWeight: '700'
    lineHeight: 38px
    letterSpacing: -0.01em
  headline-lg:
    fontFamily: Bricolage Grotesque
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 36px
    letterSpacing: -0.015em
  headline-md:
    fontFamily: Bricolage Grotesque
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 30px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Bricolage Grotesque
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 26px
  body-lg:
    fontFamily: Work Sans
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Work Sans
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Work Sans
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 18px
  price-hero:
    fontFamily: Bricolage Grotesque
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
    letterSpacing: -0.01em
  label-lg:
    fontFamily: Work Sans
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.01em
  label-md:
    fontFamily: Work Sans
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: Work Sans
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 14px
    letterSpacing: 0.04em
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-md: 1.5rem
  gutter-lg: 2rem
  margin: 1rem
  margin-md: 2rem
  margin-lg: 3rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2.5rem
---

## Brand & Style

This design system establishes an authentic wholesale marketplace interface tailored for the traditional textile commerce centers of Tamil Nadu—connecting weavers, master stockists, and retail buyers across Kanchipuram, Madurai, and Erode.

The aesthetic philosophy fuses Apple-grade clarity and calm functional restraint with the kinetic energy of physical wholesale market halls. High-clarity tabular layouts and structured inventory grids are energized by expressive typography and assertive, tactile trade controls. The interface balances high-utility wholesale mechanics—minimum order quantities, lot breakdowns, loom origin badges, and instant wholesale chat handoffs—with an uncluttered, premium white canvas.

Key visual attributes:
- **Calm, High-Utility Backdrop**: Whitespace, subtle hair-thin borders, and low-noise surfaces ensure intricate silk zari patterns, colorways, and textile weaves remain the primary visual focus.
- **Expressive Character**: The quirky, dynamic curves of Bricolage Grotesque inject regional warmth and editorial distinction into titles and pricing, while Work Sans ensures pristine, legible data grids.
- **Tactile Wholesale Utility**: Every touchpoint meets a strict 44px minimum touch target, designed for rapid inventory scanning, real-time availability confirmation, and instant quotation via direct messaging.

## Colors

The palette grounds high-frequency wholesale trading in deep heritage tones, crisp neutrals, and high-contrast status signifiers.

### Primary (`#8B1E41`)
Rich Temple Maroon represents traditional silk craftsmanship and premium trade heritage. Used for high-priority transaction anchors: active navigation states, primary "Add to Bulk Order" controls, selected lot indicators, price emphasis, and active focus boundaries.

### Secondary (`#25D366`)
Direct Trade Green serves exclusively as the functional action color for wholesale inquiries, lot negotiations, and dealer connection workflows over WhatsApp.

### Tertiary (`#16A34A`)
Emerald Stock indicator signifies live stock availability. Pairs with animated pulsing pips to denote live loom runs, active warehouse stock, and ready-to-dispatch consignments.

### Neutral (`#64748B` / Slate Neutral)
Governs non-interactive borders, metadata labels, SKU stamps, and inactive/sold-out inventory badges.

### Surface System
- Canvas Base: `#FFFFFF`
- Surface Subtle: `#F8F9FA` (Card backgrounds, table header bars)
- Surface Muted: `#F1F3F5` (Segmented tab tracks, nested lot rows)
- Hairline Border: `#E2E8F0` / `#E9ECEF` (Low-contrast structural divisions)

## Typography

Typography establishes an intentional contrast between expressive marketplace display and utilitarian wholesale data management.

- **Headline Hierarchy (`Bricolage Grotesque`)**: Deployed across market cluster names, master weaver series, lot codes, and price tags. Its distinctive character shapes deliver a curated, artisanal personality while anchoring visual priority.
- **Body & Data Layer (`Work Sans`)**: Governs specs, weight denominations (grams/warp), thread count, inventory quantities, and wholesale trade conditions. Its neutral letterforms eliminate cognitive strain during high-volume skimming.
- **Currency Convention**: All prices feature the Indian Rupee symbol (`₹`) formatted with traditional subcontinent comma grouping (e.g., `₹2,450`, `₹1,25,000`). Currency glyphs inherit the weight and family of their immediate numeric string without artificial spacing.

## Layout & Spacing

The layout model adapts to high-velocity buyers toggling between palm-held mobile devices on trading floors and wide multi-column wholesale monitors in warehouse back offices.

### Grid Architecture
- **Mobile (< 768px)**: 4-column fluid layout with `1rem` outer margins and `1rem` gutters. Inventory displays in single-column detail cards or 2-column density grids. Fixed 64px bottom navigation bar with safe-area hardware padding.
- **Tablet (768px - 1024px)**: 8-column layout with `2rem` outer margins. Accommodates dual pane views (catalog list + real-time batch detail).
- **Desktop (> 1024px)**: 12-column layout capped at 1440px max-width, framed by a persistent 280px operational sidebar. Content spans 3-column and 4-column catalog matrices with `2rem` gutters.

### Spacing Discipline
The spacing scale is locked strictly to 4px multiples. Interior card paddings strictly observe `space-md` (`1rem`) on compact views and `space-lg` (`1.5rem`) on master views. Component hit areas enforce a 44x44px minimum physical bounding box across touch viewports regardless of visible icon diameter.

## Elevation & Depth

Visual hierarchy relies on structural hairline delineation, disciplined tonal layering, and minimal diffused ambient light, rejecting heavy skeuomorphic drop shadows.

### Elevation Hierarchy
1. **Level 0 (Flat Canvas)**: `#FFFFFF` pure background for the core viewport.
2. **Level 1 (Structural Tonal Containers)**: `#F8F9FA` surfaces bounded by crisp `1px solid #E2E8F0` outlines. Used for product cards, batch spec panels, and filter groupings.
3. **Level 2 (Popped Interactive Elements)**: Low-opacity ambient shadow: `0 4px 16px -2px rgba(139, 30, 65, 0.06), 0 2px 4px -1px rgba(0, 0, 0, 0.04)` combined with `#FFFFFF` background. Used exclusively for hovered catalog tiles, sticky market summary bars, and dropdown menus.
4. **Level 3 (Modal Sheets & Live Orders)**: `0 12px 32px -4px rgba(15, 23, 42, 0.12)`, bounded by a `1px solid rgba(226, 232, 240, 0.8)` hair border. Used for mobile checkout drawers, full-lot inspect modals, and image zoom overlays.

## Shapes

The interface embraces a pill-derived geometric language (`roundedness: 3`) to contrast structured, rectangular textile photos with soft, organic interactive controls.

- **Micro Controls & Badges**: Fully pill-shaped (`rounded-full` / 9999px) for live availability indicators, weaver region tags, fiber purity seals, and quantity steppers.
- **Base Components**: Outer boundary of cards, inputs, bulk action trays, and dialogue modals utilize `rounded-xl` (1rem / 16px) for an approachable, polished form factor.
- **Nested Elements**: Inner imagery and nested thumbnail trays mirror container geometry at reduced radii (`rounded-md` / 8px) to preserve rhythmic optical margins.

## Components

### Buttons
- **Primary Wholesale Action**: `#8B1E41` solid fill, white text, 48px height, `rounded-xl` geometry, Work Sans Medium typography. Subtle scale down (0.98) on click.
- **Direct Trade Button (WhatsApp)**: `#25D366` solid fill with high-contrast `#FFFFFF` typography. Features integrated message icon, dedicated badge showing typical response time (< 5 min), and min 44px tap target.
- **Secondary / Ghost**: Transparent fill with `1px solid #E2E8F0` border, `#8B1E41` text. On hover, background shifts to `#8B1E41` at 4% opacity.

### Status Indicators & Inventory Badges
- **Available Badge**: Pill badge (`rounded-full`) with `#16A34A` at 10% opacity background and `#16A34A` label text. Accompanied by a 6px solid emerald dot with an optional concentric CSS pulse animation for live loom items.
- **Sold Out / Archived Badge**: Slate `#F1F3F5` background, `#64748B` text, `1px solid #E2E8F0`. Catalog card content dims to 60% opacity with pointer actions disabled.
- **Loom Origin Badges**: Neutral light-gray pills (`#F8F9FA`) detailing provenance (`Kanchipuram Silk Mark`, `Madurai Sungudi`, `Erode Soft Cotton`).

### Product Cards
- Contained in `1px solid #E2E8F0` cards with a `#FFFFFF` baseline surface.
- Image ratio locked to 4:5 vertical portrait to accurately display saree pleats, borders, and pallu detailing.
- Visual hierarchy: Origin tag and stock status top row; product title in `headline-sm` (`Bricolage Grotesque`); lot availability and minimum order quantity in `body-sm`; bottom pinned price tag (`₹2,450`) positioned alongside a 1-tap WhatsApp quotation trigger.

### Form Inputs & Lot Increments
- **Input Fields**: 48px height, `rounded-xl`, `#FFFFFF` interior, framed by `1px solid #CBD5E1`. Focused state transitions border to `#8B1E41` with a `0 0 0 3px rgba(139, 30, 65, 0.12)` ambient halo.
- **Quantity Steppers**: Pill-shaped control block allowing single-unit or bundle (10x, 25x, 50x) rapid wholesale additions.

### Navigation Systems
- **Mobile Bottom Navigation**: 64px persistent bar elevated above system home controls. Frosted glass finish (`rgba(255, 255, 255, 0.92)` backdrop blur 16px). Active states highlighted in `#8B1E41` with a 4px dot indicator.
- **Desktop Sidebar**: Fixed 280px left rail. Houses market switcher (e.g., Kanchipuram vs. Erode hubs), category trees, live stock counters, and buyer account status.