# Capy Tutore · Design System (Android)

Pastel-pink design system for **Capy Tutore**, the calm study-buddy app for 5th graders (Romanian UI).
Version 1.0 · exported from the Capy Tutore design canvas · 14 Oct 2026.

Open **Capy-Tutore-Design-System.pdf** for the visual overview. Everything below is also in `tokens.json`.

## What is in this folder

| Path | What it is |
|---|---|
| `Capy-Tutore-Design-System.pdf` | Visual spec: colours, type, spacing, components, icons, Capy |
| `tokens.json` | All design tokens (light + dark), machine-readable |
| `android/res/values/colors.xml` | Light colours (`@color/capy_*`) |
| `android/res/values-night/colors.xml` | Dark colours, same names — Android swaps them automatically |
| `android/res/values/themes.xml` | `Theme.CapyTutore` (Material 3 DayNight) for the manifest / splash |
| `android/res/values/dimens.xml` | Spacing, sizes, text sizes |
| `android/res/font/` | Nunito Regular / SemiBold / Bold + `nunito.xml` font family (licence: `android/NUNITO-FONT-LICENSE-OFL.txt`) |
| `android/res/drawable/ic_capy_*.xml` | 53 vector icons + 3 two-tone bow drawables |
| `android/res/drawable-nodpi/` | Floral background `bg_floral_stripes.jpg`, Capy mood PNGs |
| `android/compose/ui/theme/` | Jetpack Compose theme: `Color.kt`, `Type.kt`, `Dimens.kt`, `Theme.kt` |
| `assets/` | Original background PNG and Capy images, for design tools |

### Dropping it into the project
1. Copy `android/res/*` into `app/src/main/res/`.
2. Copy `android/compose/ui/theme/*.kt` into `app/src/main/java/md/capy/tutore/ui/theme/`.
   The package `md.capy.tutore` is a placeholder — change the `package` line and the `R` import to your real application id.
3. In `AndroidManifest.xml`: `android:theme="@style/Theme.CapyTutore"`.
4. Wrap the UI: `CapyTheme { CapyScreenBackground { … } }` and read colours as `CapyTheme.colors.right`, `CapyTheme.colors.mic`, …
   Needs Compose Material 3 and `com.google.android.material:material` (for the XML theme).

## Principles
- **Pastel pink, warm and calm.** No red, no neon, no gradients on large surfaces, no shadows (separate by colour and 1 dp lines).
- **Right = pink · Wrong = Champagne · Inactive = Linen.** A wrong answer is "not yet", never red.
- **Colour never carries meaning alone.** Right shows a heart-check icon and "Corect!", not yet shows a sprout and "Încă nu", disabled is dashed and greyed.
- **Bows are what Vali collects** (they replaced the leaves): 1–3 per lesson, spent in Capy's Corner.
- **Background:** the striped floral paper in light mode only; dark mode is plain plum so it stays quiet in the evening.
- Touch targets ≥ 48 dp with 8 dp between them; the primary button is 56 dp, full width.

## Colours (light · dark)

| Token | Role | Rol (RO) | Light | Dark |
|---|---|---|---|---|
| `capy_background` | Screen background, under the floral paper | Fundal (sub imaginea cu flori) | `#FEFAF7` | `#21171B` |
| `capy_surface` | Cards, bubbles, inputs | Carduri, bule, câmpuri | `#FFFDFE` | `#2E2227` |
| `capy_text` | Primary text (Plum cocoa) | Text principal | `#4A2F3A` | `#F6E6EB` |
| `capy_text_secondary` | Hints, metadata (Dusty mauve) | Text secundar | `#73505C` | `#CDAFBA` |
| `capy_primary` | Primary actions (Rose) | Butoane, progres, selecție | `#A8456E` | `#F2A2BF` |
| `capy_on_primary` | Content on primary | Text pe principal | `#FFFFFF` | `#21171B` |
| `capy_primary_pressed` | Primary, pressed | Principal apăsat | `#8C3A5E` | `#E58DB0` |
| `capy_secondary` | Secondary actions (Mauve) | Butoane secundare | `#8F5A70` | `#D9A5BA` |
| `capy_on_secondary` | Content on secondary | Text pe secundar | `#FFFFFF` | `#21171B` |
| `capy_secondary_pressed` | Secondary, pressed | Secundar apăsat | `#74475A` | `#C98FA6` |
| `capy_link` | Text buttons, links | Butoane text, linkuri | `#8C3A5E` | `#F2A2BF` |
| `capy_right` | Right answer (Blush pink) | Corect, idei acoperite | `#FBD9E4` | `#4A2234` |
| `capy_on_right` | Content on right (Raspberry) | Text pe corect | `#8E2A55` | `#F7B6CD` |
| `capy_right_accent` | Bow fill on right | Umplutura fundiței pe corect | `#F29BB8` | `#C25A84` |
| `capy_wrong` | Wrong / not yet (Champagne) | Încă nu (niciodată roșu) | `#F7E7CE` | `#3B3022` |
| `capy_on_wrong` | Content on wrong (Toffee) | Text pe încă nu | `#6E4A1E` | `#F3DDB8` |
| `capy_wrong_accent` | Wrong PIN dots | Puncte PIN greșit | `#9E7A40` | `#D9BC8A` |
| `capy_inactive` | Disabled / locked (Linen) | Dezactivat, blocat, gol | `#FAF0E6` | `#3A302B` |
| `capy_on_inactive` | Content on inactive | Text dezactivat | `#7E6B60` | `#A08F86` |
| `capy_inactive_outline` | Inactive outline (Deep linen) | Contur dezactivat | `#E2CFBE` | `#4D4139` |
| `capy_reward` | Bow counter, reward (Cotton candy) | Fundițe de colecționat | `#F6BFD0` | `#EBA2BC` |
| `capy_on_reward` | Content on reward | Text pe recompensă | `#4A2F3A` | `#21171B` |
| `capy_reward_banner` | Reward / info banner | Banner recompensă | `#FDE8EF` | `#432634` |
| `capy_mic` | Microphone (Dark pink) | Microfon, voce | `#E75480` | `#E75480` |
| `capy_on_mic` | Icon on microphone | Iconiță pe microfon | `#FFFFFF` | `#21171B` |
| `capy_line` | Dividers, borders | Divizori, contururi | `#F1DCE3` | `#46323B` |
| `capy_soft` | Selected tint, active nav pill | Selectat, pastilă activă | `#FCE7EE` | `#3D2531` |
| `capy_child_bubble` | Child chat bubble (Lilac pink) | Bula copilului | `#F1E3EE` | `#3E2A3F` |
| `capy_control_outline` | Radio rings, empty slots | Radio, sloturi goale | `#CDA7B6` | `#7A5F69` |
| `capy_subject_history` | Subject dot: History | Punct materie: Istorie | `#B7814A` | `#D9A57A` |
| `capy_subject_romanian` | Subject dot: Romanian | Punct materie: Română | `#8D7CC4` | `#B4A6E0` |
| `capy_subject_math` | Subject dot: Math | Punct materie: Matematică | `#A8456E` | `#F2A2BF` |
| `capy_scrim` | Scrim behind bottom sheets | Fundal sub foaia de jos | `#524A2F3A` | `#80000000` |
| `capy_mic_ring_1` | Mic pulse, outer ring | Puls microfon, inel exterior | `#29E75480` | `#26E75480` |
| `capy_mic_ring_2` | Mic pulse, inner ring | Puls microfon, inel interior | `#4DE75480` | `#47E75480` |

Contrast (WCAG): text on paper 11.5:1 · secondary text 6.7:1 · white on Rose 5.6:1 · raspberry on blush 6.2:1 · toffee on Champagne 6.5:1 · disabled text on Linen 4.5:1 · white mic icon on Dark pink 3.5:1 (icon/graphic, ≥ 3:1).
Linen is almost white, so inactive things always get the deep-linen outline `#E2CFBE` too.

## Typography — Nunito
| Style | Size / line | Weight | Example |
|---|---|---|---|
| `display` | 28/36 sp | 700 | 8 din 10 |
| `title` | 22/30 sp | 600 | Apariția omului |
| `question` | 20/28 sp | 600 | În ce țară a fost găsită Lucy? |
| `body` | 17/26 sp | 400 | Povestește-mi cu cuvintele tale tot ce ții minte din lecție. |
| `label` | 15/20 sp | 600 | Hai să începem |
| `caption` | 13/18 sp | 400 | Pasul 3 din 4 · cca 5 minute |

## Spacing, shape, motion
| Token | Value |
|---|---|
| `grid` | 8 dp |
| `screen_margin` | 16 dp |
| `block_gap` | 24 dp |
| `item_gap` | 8 dp |
| `radius_card` | 16 dp |
| `radius_button` | 24 dp |
| `radius_bubble` | 24 dp |
| `radius_small` | 12 dp |
| `button_height` | 56 dp |
| `button_height_small` | 48 dp |
| `touch_target` | 48 dp |
| `mic_button` | 96 dp |
| `mic_button_small` | 48 dp |
| `icon` | 24 dp |
| `icon_small` | 18 dp |
| `top_bar_height` | 76 dp |
| `bottom_nav_height` | 72 dp |
| `answer_card_min_height` | 56 dp |
| `chip_height` | 40 dp |
| `stroke_icon` | 2 dp |
| Corners | cards 16 · buttons & bubbles 24 · chips pill · small 12 |
| Motion | 200–300 ms ease-out · mic pulse 2 s |
| Elevation | none — colour and lines only |

## Components (states)
- **Primary button** — Rose, white label, 56 dp, 24 dp corners. Pressed `primary_pressed`. Disabled: Linen + `on_inactive`. Loading: three slow dots.
- **Secondary button** — Mauve, white label. **Text button** — `link` colour; pressed shows a `soft` pill.
- **Mic button (96 dp)** — Dark pink with white mic. Recording: two pulsing rings (`mic_ring_1/2`), pause icon. Paused: white with a 3 dp dark-pink ring. Disabled: Linen with dashed outline ("Permite microfonul").
- **Capy bubble** — surface + 1 dp line, tail bottom-left, speaker button to read aloud (ro-RO). Reading aloud: 2 dp dark-pink border.
- **Child bubble** — lilac pink, tail bottom-right. Right → blush pink + heart-check. Not yet → Champagne + sprout, with "Cred că am dreptate" link.
- **Answer card (min 56 dp)** — default (radio ring `control_outline`), selected (`soft` + 2 dp Rose), right, not yet, disabled (Linen).
- **Order / match cards** — drag handle, dragging state with Rose border, placed state with number badge.
- **Bows & chips** — earned bow (cotton-candy fill), empty bow (Linen), idea chips: known (pink + heart-check), to practise (Champagne + sprout), hint chip (reward outline).
- **Lesson row** — subject dot, title, date, 0–3 bows or "de recapitulat" badge.
- **Feedback bottom sheet** — right (blush) / not yet (Champagne), explanation, primary button.
- **Bottom navigation (3 tabs)** — active: `soft` pill + Rose icon + bold label; inactive: `text_secondary`.
- **Banners** — offline (`soft`), error (Champagne), info / reward (`reward_banner` + bow).
- **PIN pad** — empty rings, filled Rose dots, wrong = Champagne panel with toffee dots, no shake.

## Icons
24 dp grid, 2 dp round stroke (Material Symbols Rounded equivalents). Tint with `Icon(painterResource(R.drawable.ic_capy_mic), null, tint = CapyTheme.colors.onMic)`.
Use `ic_capy_bow_earned` / `ic_capy_bow_empty` / `ic_capy_bow_right` with `Image()` to keep the two-tone bow.

`home`, `review`, `corner`, `bow`, `checkheart`, `sprout`, `camera`, `link`, `mic`, `send`, `close`, `speaker`, `plus`, `book`, `hint`, `keyboard`, `pause`, `play`, `lock`, `chevron`, `back`, `drag`, `swap`, `check`, `offline`, `page`, `photo`, `clock`, `sliders`, `chart`, `flag`, `trash`, `backspace`, `list`, `truefalse`, `timeline`, `match`, `edit`, `user`, `bell`, `moon`, `history`, `romanian`, `math`, `science`, `geo`, `more`, `cup`, `lamp`, `plant`, `shelf`, `cushion`, `window`

## Capy
Same Capy as in Capy Pomodoro, transparent PNGs: `capy_neutral` (neutru), `capy_listening` (ascultă), `capy_thinking` (se gândește), `capy_reading` (citește), `capy_happy` (fericit), `capy_encouraging` (încurajează), `capy_sleepy` (somnoros), `capy_face` (față (iconiță)).
"Confused" = `capy_thinking` with a small "?" badge in reward pink.
