# Charts

MacKay's two translation charts are nomograms: several scales side by side, all of them linear in the same underlying quantity, so that one horizontal line drawn across them reads the same amount in every unit at once. On paper you lay a ruler across. Here you drag the line.

*Added in the 2026 revision: the charts below are interactive, and they carry today's numbers as well as his. MacKay's printed originals follow each one, unchanged. His scales stopped where his own marks needed them to — 125 kWh/d per person on the power chart, 11 tonnes of CO<sub>2</sub> a year on the carbon one — and both are extended here, because an American now uses 206 kWh/d and emits 14.2 tonnes.*

## Power translation chart

::: {#power-nomogram}
```{=html}
<div class="nomogram" data-chart="power"></div>
```
:::

![power translational chart](/img/without-hot-air/power-chart.gif)

<span class="figurenumber">MacKay's printed power translation chart</span>, kept for reference. The interactive version above uses the same four scales and the same conversions: 60 million people to a "UK", so that 1 kWh/d per person is 2.5 GW; 1 GW running all year is 8.76 TWh; and 1 Mtoe is 11.63 TWh.

## Carbon translation chart

::: {#carbon-nomogram}
```{=html}
<div class="nomogram" data-chart="carbon"></div>
```
:::

![carbon translation vhart](/img/without-hot-air/carbon-chart.gif)

<span class="figurenumber">MacKay's printed carbon translation chart</span>, kept for reference. The interactive version above uses his exchange rates: 1 kWh of chemical energy is 250 g of CO<sub>2</sub> for oil or petrol, 1 kWh of electricity is 445 g for gas, a "UK" is 60 million people and a "World" is 6 billion, and a tonne of carbon is 44/12 tonnes of CO<sub>2</sub>.

```{=html}
<style>
.nomogram{border:1px solid #dfe1de;border-radius:6px;padding:14px 16px;margin:18px 0;font-size:14px}
.nomogram .nomo-top{display:flex;flex-wrap:wrap;gap:10px;align-items:center;margin-bottom:8px}
.nomogram .nomo-presets{display:flex;flex-wrap:wrap;gap:6px}
/* Colours follow whatever the page is set to rather than the browser's own
   preference: the book renders light even in a dark browser, and a
   prefers-color-scheme rule here would leave black boxes on a white page. */
.nomogram button{font-size:12.5px;padding:4px 9px;border:1px solid currentColor;
  background:transparent;color:inherit;opacity:.75;border-radius:4px;cursor:pointer}
.nomogram button:hover{opacity:1}
.nomogram svg{width:100%;height:auto;display:block;touch-action:none;cursor:ns-resize}
.nomogram .nomo-foot{display:flex;flex-wrap:wrap;gap:14px;margin-top:10px}
.nomogram .nomo-field{display:flex;flex-direction:column;gap:2px;font-size:12.5px}
.nomogram .nomo-field label{color:#46534f}
.nomogram input{width:9.5em;font:13px/1.4 inherit;padding:3px 5px;border:1px solid currentColor;
  background:transparent;color:inherit;border-radius:4px}
.nomogram .nomo-hint{color:#7b8683;font-size:12.5px;margin-top:6px}
.nomo-axis{stroke:#c9c9c4;stroke-width:1}
.nomo-tick{stroke:#dfe1de;stroke-width:1}
.nomo-tick-label,.nomo-title{fill:#46534f;font-size:10px}
.nomo-title{font-size:11px;font-weight:600}
.nomo-line{stroke:#bf4433;stroke-width:1.6}
.nomo-read{fill:#bf4433;font-size:11px;font-weight:600}
.nomo-knob{fill:#bf4433}
body.quarto-dark .nomo-tick-label,body.quarto-dark .nomo-title{fill:#b6bdba}
body.quarto-dark .nomo-axis{stroke:#555}
body.quarto-dark .nomo-tick{stroke:#3a3a3a}
</style>
<script type="module">
// The two charts of MacKay's appendix, as nomograms rather than pictures of
// nomograms. Every scale is a fixed multiple of one underlying quantity, which
// is why a single horizontal line reads correctly across all of them; that is
// also what makes this cheap to compute rather than to draw.
const CHARTS = {
  power: {
    // base unit: kWh per day per person, with 60 million people to a "UK".
    // MacKay's printed chart stops at 125, which fitted the UK of 2004; the
    // scale here runs to 210 so that today's American can be marked on it.
    base: {max: 210},
    scales: [
      {name: "kWh/d/p", factor: 1, step: 25, decimals: 1},
      {name: "GW / UK", factor: 2.5, step: 50, decimals: 1},
      {name: "TWh/y / UK", factor: 2.5 * 8.76, step: 500, decimals: 0},
      {name: "Mtoe/y / UK", factor: 2.5 * 8.76 / 11.63, step: 50, decimals: 1},
    ],
    presets: [
      ["UK total, 2004", 122], ["UK electricity fuel input, 2004", 47],
      ["UK electricity, 2004", 18.3], ["UK nuclear, 2004", 4.6],
    ],
    // filled in from book/assets/chart-marks.json, with these as the fallback
    live: {key: "energy", year: 2025, label: y => `, ${y}`, values:
      {"United States": 205.6, "Sweden": 118.2, "China": 87.2,
       "United Kingdom": 68.9, "World": 55.5, "India": 20.3}},
    hint: "Drag the line, type in any box, or take one of the marks. The 2004 " +
          "marks are read off MacKay's printed chart; the rest are primary " +
          "energy per person from the series behind chapter L's figure L.1.",
  },
  carbon: {
    // base unit: tonnes of CO2 per year per person
    // his chart stops at 11 tonnes, which fitted the UK of 1990; 15 is enough
    // for today's American.
    base: {max: 15},
    scales: [
      {name: "kWh/d/p", factor: 1 / (0.250 * 365 / 1000), step: 20, decimals: 1},
      {name: "kWh(e)/d/p", factor: 1 / (0.445 * 365 / 1000), step: 10, decimals: 1},
      {name: "tCO₂/y/p", factor: 1, step: 1, decimals: 2},
      {name: "MtCO₂/y / UK", factor: 60, step: 100, decimals: 0},
      {name: "GtCO₂/y / World", factor: 6, step: 10, decimals: 1},
      {name: "GtC/y / World", factor: 6 * 12 / 44, step: 5, decimals: 2},
    ],
    presets: [
      ["UK 1990, 600 MtCO₂", 10], ["60% cut on 1990", 4],
      ["80% cut on 1990", 2], ["90% cut on 1990", 1],
      ["UK electricity, 20 kWh(e)/d", 20 * 0.445 * 365 / 1000],
    ],
    live: {key: "co2", year: 2024, label: y => `, ${y}`, values:
      {"United States": 14.20, "China": 8.66, "World": 4.73,
       "United Kingdom": 4.53, "Sweden": 3.59, "India": 2.20}},
    hint: "Drag the line, type in any box, or take one of the marks. The targets " +
          "are measured on the UK's 1990 emissions, as MacKay's chart measures " +
          "them; the countries are CO₂ per person from this edition's own data.",
  },
};
const W = 820, H = 420, TOP = 34, BOTTOM = 42, PAD = 60;
const ns = "http://www.w3.org/2000/svg";
const el = (n, a = {}) => {
  const e = document.createElementNS(ns, n);
  for (const [k, v] of Object.entries(a)) e.setAttribute(k, v);
  return e;
};

function build(node, spec) {
  const top = document.createElement("div"); top.className = "nomo-top";
  const presets = document.createElement("div"); presets.className = "nomo-presets";
  top.appendChild(presets); node.appendChild(top);
  const svg = el("svg", {viewBox: `0 0 ${W} ${H}`, role: "img",
    "aria-label": `Translation chart: ${spec.scales.map(s => s.name).join(", ")}`});
  node.appendChild(svg);
  const foot = document.createElement("div"); foot.className = "nomo-foot";
  node.appendChild(foot);
  const hint = document.createElement("p");
  hint.className = "nomo-hint"; hint.textContent = spec.hint;
  node.appendChild(hint);

  const n = spec.scales.length;
  const x = i => PAD + i * (W - 2 * PAD) / (n - 1);
  const y = v => H - BOTTOM - (v / spec.base.max) * (H - TOP - BOTTOM);
  const valueAt = py => Math.min(spec.base.max, Math.max(0,
    (H - BOTTOM - py) / (H - TOP - BOTTOM) * spec.base.max));

  spec.scales.forEach((s, i) => {
    svg.appendChild(el("line", {class: "nomo-axis", x1: x(i), x2: x(i), y1: y(0), y2: y(spec.base.max)}));
    const title = el("text", {class: "nomo-title", x: x(i), y: TOP - 14, "text-anchor": "middle"});
    title.textContent = s.name; svg.appendChild(title);
    const topValue = spec.base.max * s.factor;
    for (let t = 0; t <= topValue + 1e-9; t += s.step) {
      const v = t / s.factor;
      if (v > spec.base.max + 1e-9) break;
      svg.appendChild(el("line", {class: "nomo-tick", x1: x(i) - 4, x2: x(i) + 4, y1: y(v), y2: y(v)}));
      const lab = el("text", {class: "nomo-tick-label", x: x(i) - 8, y: y(v) + 3.5, "text-anchor": "end"});
      lab.textContent = t.toLocaleString(undefined, {maximumFractionDigits: 1});
      svg.appendChild(lab);
    }
  });

  const line = el("line", {class: "nomo-line", x1: PAD - 18, x2: W - PAD + 18});
  const knob = el("circle", {class: "nomo-knob", r: 5, cx: PAD - 18});
  svg.appendChild(line); svg.appendChild(knob);
  const readouts = spec.scales.map((s, i) => {
    const t = el("text", {class: "nomo-read", x: x(i) + 8, "text-anchor": "start"});
    svg.appendChild(t); return t;
  });
  const inputs = spec.scales.map((s, i) => {
    const wrap = document.createElement("div"); wrap.className = "nomo-field";
    const id = `${spec.id}-in-${i}`;
    const label = document.createElement("label"); label.htmlFor = id; label.textContent = s.name;
    const input = document.createElement("input");
    input.type = "text"; input.inputMode = "decimal"; input.id = id;
    input.addEventListener("input", () => {
      const parsed = parseFloat(input.value.replace(/[^\d.eE+-]/g, ""));
      if (!isNaN(parsed)) set(parsed / s.factor, input);
    });
    wrap.appendChild(label); wrap.appendChild(input); foot.appendChild(wrap);
    return input;
  });

  let value = spec.presets[0][1], raf = 0, arrive = 0;
  function draw(skip) {
    const py = y(value);
    line.setAttribute("y1", py); line.setAttribute("y2", py);
    knob.setAttribute("cy", py);
    spec.scales.forEach((s, i) => {
      const v = value * s.factor;
      readouts[i].setAttribute("y", py - 6);
      readouts[i].textContent = v.toLocaleString(undefined,
        {maximumFractionDigits: s.decimals});
      if (inputs[i] !== skip) inputs[i].value = v.toFixed(s.decimals);
    });
  }
  function set(v, skip) {
    value = Math.min(spec.base.max, Math.max(0, v));
    draw(skip);
  }
  // Presets ease into place rather than jumping, so the eye can follow which
  // way every scale moved.
  function glide(target) {
    cancelAnimationFrame(raf); clearTimeout(arrive);
    const from = value, t0 = performance.now(), ms = 550;
    const step = now => {
      const k = Math.min(1, (now - t0) / ms);
      set(from + (target - from) * (1 - Math.pow(1 - k, 3)));
      if (k < 1) raf = requestAnimationFrame(step);
    };
    raf = requestAnimationFrame(step);
    // A background tab gets no animation frames, and a mark that never
    // arrives is worse than one that arrives without the animation.
    arrive = setTimeout(() => { cancelAnimationFrame(raf); set(target); }, ms + 50);
  }
  spec.presets.forEach(([name, v]) => {
    const b = document.createElement("button");
    b.type = "button"; b.textContent = name;
    b.addEventListener("click", () => glide(v));
    presets.appendChild(b);
  });

  const pointerValue = ev => {
    const r = svg.getBoundingClientRect();
    return valueAt((ev.clientY - r.top) * H / r.height);
  };
  let dragging = false;
  svg.addEventListener("pointerdown", ev => {
    dragging = true; svg.setPointerCapture(ev.pointerId);
    cancelAnimationFrame(raf); set(pointerValue(ev));
  });
  svg.addEventListener("pointermove", ev => { if (dragging) set(pointerValue(ev)); });
  svg.addEventListener("pointerup", () => { dragging = false; });
  draw();
}

// Today's marks come from book/assets/chart-marks.json, which a data task
// writes from the same series the chapters use. The values compiled into the
// page are the fallback, so the charts still work opened from a file.
async function marks() {
  try {
    const url = new URL("../assets/chart-marks.json", document.baseURI).href;
    const r = await fetch(url);
    if (r.ok) return await r.json();
  } catch (e) { /* offline or opened from disk: keep the built-in values */ }
  return null;
}
marks().then(data => {
  for (const spec of Object.values(CHARTS)) {
    const live = spec.live;
    if (!live) continue;
    const values = (data && data[live.key]) || live.values;
    const year = (data && data[live.key === "co2" ? "co2Year" : "energyYear"]) || live.year;
    for (const [name, v] of Object.entries(values))
      spec.presets.push([`${name}${year ? live.label(year) : ""}`, v]);
  }
  document.querySelectorAll(".nomogram").forEach((node, i) => {
    const spec = CHARTS[node.dataset.chart];
    if (spec) { spec.id = `nomo-${i}`; build(node, spec); }
  });
});
</script>
```
