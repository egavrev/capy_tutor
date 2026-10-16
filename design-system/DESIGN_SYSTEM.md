# Capy Tutore · Design System (for Claude Code)

Read this before writing or changing any UI. It is the single source of truth for how Capy Tutore looks.
The full package (PDF guide, tokens, resources) lives in `design-system/`; the visual reference is
`design-system/Capy-Tutore-Design-System.pdf`.

## The app in one paragraph
Capy Tutore is a calm Android study-buddy for a 5th grader (Romanian UI, Moldova curriculum). Capy the
capybara (same mascot as Capy Pomodoro) reads a lesson, the child retells it by voice, Capy shows which
ideas were remembered, asks guided questions, then runs a gentle final test. Kotlin + Jetpack Compose +
Material 3. All UI text is **Romanian** (use ș ț with comma below, never ş ţ).

## Hard rules (never break these)
1. **Never hardcode colours, sizes or fonts.** Use `CapyTheme.colors.*`, `CapyTheme.type.*`, `CapyDimens.*`,
   `CapyShapes.*`. If a value is missing, add a token to the theme files and to `design-system/tokens.json` — do not inline a hex.
2. **Right = pink, Wrong = Champagne, Inactive = Linen.** Right: `right` / `onRight`. Wrong ("Încă nu", never
   "Greșit"): `wrong` / `onWrong`. Disabled, locked or empty: `inactive` / `onInactive` with an `inactiveOutline` border.
   **Never use red**, never `MaterialTheme.colorScheme.error` styling for wrong answers.
3. **Colour never carries meaning alone.** Right always shows the `checkheart` icon + words ("Corect!");
   wrong shows `sprout` + "Încă nu"; disabled shows a dashed outline and lighter text.
4. **Bows, not leaves.** The child collects bows (`fundițe`): 0–3 per lesson, spent in Capy's Corner.
   Use `ic_capy_bow_earned` / `ic_capy_bow_empty` / `ic_capy_bow_right` (two-tone, draw with `Image`), or `ic_capy_bow` tinted.
   Copy: "fundiță" (1), "fundițe" (plural), e.g. "Ai câștigat 3 fundițe pentru Capy!".
5. **The microphone is dark pink** (`mic` #E75480, icon `onMic`). 96 dp round on the Recall screen, 48 dp inline.
6. **Background:** every full screen sits in `CapyScreenBackground { }` — striped floral paper in light mode,
   plain plum in dark mode. Content goes on `surface` cards with a 1 dp `line` border so text stays readable.
7. **No shadows, no gradients, no neon.** Separate by colour and 1 dp lines. Elevation 0 on Cards/Surfaces.
8. **Accessibility:** touch targets ≥ 48 dp with ≥ 8 dp between; primary button 56 dp full width; text sizes in `sp`;
   every icon-only button gets a Romanian `contentDescription`; decorative images get `contentDescription = null`.
9. **Dark mode follows the system** (`isSystemInDarkTheme()`); never force light. Test both.
10. **Calm motion:** 200–300 ms ease-out (`CapyMotion`), mic pulse 2 s. No shake on errors.

## Where things live (after the package is copied into the project)
| What | Path |
|---|---|
| Colours light / dark | `app/src/main/res/values/colors.xml`, `values-night/colors.xml` (`@color/capy_*`) |
| XML theme (manifest, splash) | `app/src/main/res/values/themes.xml` → `Theme.CapyTutore` |
| Dimensions | `app/src/main/res/values/dimens.xml` |
| Font | `app/src/main/res/font/nunito*.ttf` + `nunito.xml` |
| Icons | `app/src/main/res/drawable/ic_capy_*.xml` (24 dp, 2 dp round stroke) |
| Background, Capy images | `app/src/main/res/drawable-nodpi/bg_floral_stripes.jpg`, `capy_*.png` |
| Compose theme | `app/src/main/java/<package>/ui/theme/` → `Color.kt`, `Type.kt`, `Dimens.kt`, `Theme.kt` |
| Tokens (source of truth) | `design-system/tokens.json` |

The theme files ship with the placeholder package `md.capy.tutore` — update the `package` line and the `R` import to the real application id.

## Colour tokens
`CapyTheme.colors.<name>` in Compose · `@color/capy_<name>` in XML.

| Compose | XML | Light | Dark | Use |
|---|---|---|---|---|
| `background` | `capy_background` | `#FEFAF7` | `#21171B` | Screen background, under the floral paper |
| `surface` | `capy_surface` | `#FFFDFE` | `#2E2227` | Cards, bubbles, inputs |
| `text` | `capy_text` | `#4A2F3A` | `#F6E6EB` | Primary text (Plum cocoa) |
| `textSecondary` | `capy_text_secondary` | `#73505C` | `#CDAFBA` | Hints, metadata (Dusty mauve) |
| `primary` | `capy_primary` | `#A8456E` | `#F2A2BF` | Primary actions (Rose) |
| `onPrimary` | `capy_on_primary` | `#FFFFFF` | `#21171B` | Content on primary |
| `primaryPressed` | `capy_primary_pressed` | `#8C3A5E` | `#E58DB0` | Primary, pressed |
| `secondary` | `capy_secondary` | `#8F5A70` | `#D9A5BA` | Secondary actions (Mauve) |
| `onSecondary` | `capy_on_secondary` | `#FFFFFF` | `#21171B` | Content on secondary |
| `secondaryPressed` | `capy_secondary_pressed` | `#74475A` | `#C98FA6` | Secondary, pressed |
| `link` | `capy_link` | `#8C3A5E` | `#F2A2BF` | Text buttons, links |
| `right` | `capy_right` | `#FBD9E4` | `#4A2234` | Right answer (Blush pink) |
| `onRight` | `capy_on_right` | `#8E2A55` | `#F7B6CD` | Content on right (Raspberry) |
| `rightAccent` | `capy_right_accent` | `#F29BB8` | `#C25A84` | Bow fill on right |
| `wrong` | `capy_wrong` | `#F7E7CE` | `#3B3022` | Wrong / not yet (Champagne) |
| `onWrong` | `capy_on_wrong` | `#6E4A1E` | `#F3DDB8` | Content on wrong (Toffee) |
| `wrongAccent` | `capy_wrong_accent` | `#9E7A40` | `#D9BC8A` | Wrong PIN dots |
| `inactive` | `capy_inactive` | `#FAF0E6` | `#3A302B` | Disabled / locked (Linen) |
| `onInactive` | `capy_on_inactive` | `#7E6B60` | `#A08F86` | Content on inactive |
| `inactiveOutline` | `capy_inactive_outline` | `#E2CFBE` | `#4D4139` | Inactive outline (Deep linen) |
| `reward` | `capy_reward` | `#F6BFD0` | `#EBA2BC` | Bow counter, reward (Cotton candy) |
| `onReward` | `capy_on_reward` | `#4A2F3A` | `#21171B` | Content on reward |
| `rewardBanner` | `capy_reward_banner` | `#FDE8EF` | `#432634` | Reward / info banner |
| `mic` | `capy_mic` | `#E75480` | `#E75480` | Microphone (Dark pink) |
| `onMic` | `capy_on_mic` | `#FFFFFF` | `#21171B` | Icon on microphone |
| `line` | `capy_line` | `#F1DCE3` | `#46323B` | Dividers, borders |
| `soft` | `capy_soft` | `#FCE7EE` | `#3D2531` | Selected tint, active nav pill |
| `childBubble` | `capy_child_bubble` | `#F1E3EE` | `#3E2A3F` | Child chat bubble (Lilac pink) |
| `highlight` | `capy_highlight` | `#F9D3E0` | `#5A2E43` | Key sentences in reading mode (E14), always with semi-bold text |
| `sunny` | `capy_sunny` | `#F4C35A` | `#E2B455` | Warm yellow inside illustrations only (egg yolk, lantern flame, lamp bulb in the intro); never for text or a state |
| `controlOutline` | `capy_control_outline` | `#CDA7B6` | `#7A5F69` | Radio rings, empty slots |
| `subjectHistory` | `capy_subject_history` | `#B7814A` | `#D9A57A` | Subject dot: History |
| `subjectRomanian` | `capy_subject_romanian` | `#8D7CC4` | `#B4A6E0` | Subject dot: Romanian |
| `subjectMath` | `capy_subject_math` | `#A8456E` | `#F2A2BF` | Subject dot: Math |
| `scrim` | `capy_scrim` | `#524A2F3A` | `#80000000` | Scrim behind bottom sheets |
| `micRing1` | `capy_mic_ring_1` | `#29E75480` | `#26E75480` | Mic pulse, outer ring |
| `micRing2` | `capy_mic_ring_2` | `#4DE75480` | `#47E75480` | Mic pulse, inner ring |

Material 3 mapping (done in `CapyTheme`): primary = Rose, secondary = Mauve, tertiary = mic, background/surface as above,
errorContainer = Champagne (only for "not yet", never shown as an error). Prefer the `CapyTheme.colors` names in app code.

## Typography — Nunito
`CapyTheme.type.<style>`

| Style | Size / line height | Weight | Example |
|---|---|---|---|
| `display` | 28 sp / 36 sp | 700 | 8 din 10 |
| `title` | 22 sp / 30 sp | 600 | Apariția omului |
| `question` | 20 sp / 28 sp | 600 | În ce țară a fost găsită Lucy? |
| `body` | 17 sp / 26 sp | 400 | Povestește-mi cu cuvintele tale tot ce ții minte din lecție. |
| `label` | 15 sp / 20 sp | 600 | Hai să începem |
| `caption` | 13 sp / 18 sp | 400 | Pasul 3 din 4 · cca 5 minute |

Screen titles = `display` or `title`; quiz questions = `question`; Capy's speech and body = `body`; buttons, chips = `label`; metadata = `caption` in `textSecondary`.

## Spacing, shape, motion
| Token | Value |
|---|---|
| `CapyDimens.grid` | 8 dp |
| `CapyDimens.screenMargin` | 16 dp |
| `CapyDimens.blockGap` | 24 dp |
| `CapyDimens.itemGap` | 8 dp |
| `CapyDimens.radiusCard` | 16 dp |
| `CapyDimens.radiusButton` | 24 dp |
| `CapyDimens.radiusBubble` | 24 dp |
| `CapyDimens.radiusSmall` | 12 dp |
| `CapyDimens.buttonHeight` | 56 dp |
| `CapyDimens.buttonHeightSmall` | 48 dp |
| `CapyDimens.touchTarget` | 48 dp |
| `CapyDimens.micButton` | 96 dp |
| `CapyDimens.micButtonSmall` | 48 dp |
| `CapyDimens.icon` | 24 dp |
| `CapyDimens.iconSmall` | 18 dp |
| `CapyDimens.topBarHeight` | 76 dp |
| `CapyDimens.bottomNavHeight` | 72 dp |
| `CapyDimens.answerCardMinHeight` | 56 dp |
| `CapyDimens.chipHeight` | 40 dp |
| `CapyDimens.strokeIcon` | 2 dp |

Shapes: `CapyShapes.card` 16 dp · `button` 24 dp · `chip` pill · `small` 12 dp · `capyBubble` (tail bottom-left) · `childBubble` (tail bottom-right) · `bottomSheet` (top 24 dp).
Layout: 16 dp side margins, 24 dp between big blocks, 8 dp grid. Phone portrait first (360 × 800 dp reference).

## Component recipes
Build these as reusable composables in `ui/components/` and reuse them; do not restyle per screen.

| Component | Spec |
|---|---|
| Primary button | `primary` / `onPrimary`, 56 dp, full width, `CapyShapes.button`, `label` style. Pressed `primaryPressed`. Disabled `inactive` / `onInactive` + dashed `inactiveOutline`. Loading = 3 slow dots, no spinner. |
| Secondary button | `secondary` / `onSecondary`, same size. Pressed `secondaryPressed`. |
| Text button | `link` colour, 48 dp tall; pressed shows a `soft` pill. |
| Mic button | 96 dp circle `mic`, `ic_capy_mic` 40 dp in `onMic`. Recording: `ic_capy_pause` + two pulsing rings `micRing2` (116 dp) and `micRing1` (136 dp), 2 s. Paused: `surface` fill, 3 dp `mic` border, mic icon in `mic`. No permission: `inactive` + dashed outline, label "Permite microfonul". |
| Capy bubble | `surface`, 1 dp `line`, `capyBubble` shape, `body` text, speaker button (48 dp) to read aloud in ro-RO; max 4 lines then scroll. While reading aloud: 2 dp `mic` border. |
| Child bubble | `childBubble` color, `childBubble` shape, right-aligned. Right → `right`/`onRight` + `checkheart`. Not yet → `wrong`/`onWrong` + `sprout`, with link "Cred că am dreptate". |
| Answer card | min 56 dp, `card` shape. Default: `surface` + 1 dp `line` + radio ring `controlOutline`. Selected: `soft` + 2 dp `primary`. Right: `right` + `checkheart`. Not yet: `wrong` + `sprout`. Disabled: `inactive`. |
| Order / match card | drag handle `ic_capy_drag`; dragging = 2 dp `primary` border; placed = `soft` + number badge in `primary`. Matched pair = `right` + `link` icon. |
| Bow counter | pill in `reward`, bow icon (`onReward` stroke, `surface` fill) + number, 48 dp tall, opens Capy's Corner. |
| Bow row (lesson) | up to 3 bows 18 dp: earned `ic_capy_bow_earned`, not earned `ic_capy_bow_empty`. Content description "2 din 3 fundițe". |
| Idea chip | 40 dp pill. Known: `right` + `checkheart`. To practise: `wrong` + `sprout`. Hint: 2 dp `reward` outline + `hint` icon in `secondary`. Subject chip selected: `primary` + check. |
| Feedback bottom sheet | `bottomSheet` shape. Right: `right`, title "Corect!" + `checkheart`. Not yet: `wrong`, "Încă nu. Răspunsul este: …" + `sprout`. Explanation in `text`, primary button "Mai departe". Scrim `scrim`. |
| Lesson row | 60 dp, `surface`, `card` shape, subject dot 10 dp (`subjectHistory` / `subjectRomanian` / `subjectMath`), title `body`, date `caption`, bows or "de recapitulat" badge. |
| Bottom navigation | 72 dp, `surface` + top `line`. 3 tabs: Acasă (`home`), Recapitulare (`review`), Colțul lui Capy (`corner`). Active: `soft` pill 60×32 + `primary` icon + bold label. Inactive: `textSecondary`. |
| Banners | 16 dp radius. Offline: `soft` + `offline` icon. Error: `wrong` + `sprout` + "Reîncearcă". Info / reward: `rewardBanner` + bow. |
| PIN pad (parents) | keys 56 dp `background`, pressed `soft`. Dots: empty ring `controlOutline`, filled `primary`. Wrong: `wrong` panel with `wrongAccent` dots, gentle text, no shake. |
| Reading page (E14) | Paragraph = `surface` card, 1 dp `line`; key sentence = `highlight` background + semi-bold; paragraph read aloud = 2 dp `mic` border; 48 dp speaker button per paragraph. |
| Progress (4 steps) | Lecție · Povestește · Întrebări · Test; 6 dp bars, done `primary`, todo `line`, current step label bold. |

### Minimal usage
```kotlin
setContent {
    CapyTheme {
        CapyScreenBackground {
            HomeScreen()
        }
    }
}

@Composable
fun CapyPrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = CapyTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CapyShapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = c.primary, contentColor = c.onPrimary,
            disabledContainerColor = c.inactive, disabledContentColor = c.onInactive,
        ),
        elevation = null,
        modifier = Modifier.fillMaxWidth().height(CapyDimens.buttonHeight),
    ) { Text(text, style = CapyTheme.type.label) }
}
```

## Icons
`painterResource(R.drawable.ic_capy_<name>)`, tint with `Icon(..., tint = CapyTheme.colors.xxx)`. Available:
home, review, corner, bow, checkheart, sprout, camera, link, mic, send, close, speaker, plus, book, hint, keyboard, pause, play, lock, chevron, back, drag, swap, check, offline, page, photo, clock, sliders, chart, flag, trash, backspace, list, truefalse, timeline, match, edit, user, bell, moon, history, romanian, math, science, geo, more, cup, lamp, plant, shelf, cushion, window.
Bows with fill: `ic_capy_bow_earned`, `ic_capy_bow_empty`, `ic_capy_bow_right` (use `Image`, not `Icon`).
Need a new icon? Match the style: 24 dp grid, 2 dp stroke, round caps and joins, no fill (Material Symbols Rounded look).

## Capy (mascot)
Transparent PNGs in `drawable-nodpi`: `capy_neutral` (neutru), `capy_listening` (ascultă), `capy_thinking` (se gândește), `capy_reading` (citește), `capy_happy` (fericit), `capy_encouraging` (încurajează), `capy_sleepy` (somnoros), `capy_face` (față (iconiță)).
"Confused" = `capy_thinking` + a small "?" badge in `reward`. Use `listening` while recording, `reading` while a lesson loads,
`happy` for right answers / high scores, `encouraging` for "not yet" and low scores, `sleepy` for empty review, `face` in top bars (48 dp).
Capy is never sad, never scolds.

## Voice and copy
Warm, short, encouraging, second person singular ("tu"). Mistakes are "Încă nu", never "Greșit". No timers or pressure
("Fără grabă, fără cronometru"). Greet the child by name ("Bună, Vali!").

## Checklist before finishing any UI change
- [ ] Only theme tokens used (search the diff for `Color(0x` and `.dp` literals outside the theme files)
- [ ] Right / wrong / inactive use pink / Champagne / Linen + icon + words
- [ ] Looks right in light (floral paper) and dark mode
- [ ] Touch targets ≥ 48 dp, content descriptions in Romanian
- [ ] All copy in Romanian with correct diacritics
