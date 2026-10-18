# Agronomy notes – v1.5.6 patch

- ThingSpeak is preferred, Open-Meteo is fallback in automatic input mode, and manual entry remains editable.
- EC sensor values are treated as valid project inputs. Unit conversion only converts µS/cm to dS/m; the app does not require ECe. ECe is optional laboratory reference.
- No universal EC-sensor→ECe conversion is hard-coded because published conversion factors vary with extraction ratio, texture and soil conditions.
- Bulk-density presets: mineral 1.30 g/cm³, peat 0.30 g/cm³, or custom. Presets are reference assumptions.
- History management supports delete selected or delete all for field notes, fertilizer history and OPT history.
- Long formulas/scientific caveats are moved out of the main analysis view into clickable NOTE dialogs.

References: Seo et al. (2022), Pedosphere 32(6), DOI 10.1016/j.pedsph.2022.06.023; Journal of the Saudi Society of Agricultural Sciences 23(4), DOI 10.1016/j.jssas.2023.12.005.
