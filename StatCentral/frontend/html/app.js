// StatCentral front end — talks to the GatewayServer's /request and /status
// endpoints (proxied same-origin by nginx, see nginx.conf) using the exact
// JSON request shapes documented in FanInterfaceImpl / LeagueInterfaceImpl /
// WorkerServer.dispatchRequest().

const INT_FIELDS = new Set([
  "year", "topN", "homeRuns", "hits", "atBats", "strikeouts",
  "walks", "stolenBases", "runsBattedIn", "value"
]);
const FLOAT_FIELDS = new Set(["battingAverage"]);

const resultEl = document.getElementById("result");
const leagueSelect = document.getElementById("leagueId");

function setResult(text, kind) {
  resultEl.textContent = text;
  resultEl.classList.remove("success", "error");
  if (kind) resultEl.classList.add(kind);
}

async function postRequest(body) {
  setResult("Sending request...", null);
  try {
    const res = await fetch("/request", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });
    const text = await res.text();
    const looksLikeError = !res.ok || /^error/i.test(text.trim());
    setResult(text || `(empty response, HTTP ${res.status})`, looksLikeError ? "error" : "success");
  } catch (err) {
    setResult("Request failed: " + err.message
      + "\n\nIs the StatCentral app container running and has the cluster finished electing a leader? "
      + "Try the \"Check cluster status\" button above.", "error");
  }
}

async function checkStatus() {
  const statusOutput = document.getElementById("statusOutput");
  statusOutput.textContent = "Checking...";
  try {
    const res = await fetch("/status");
    const text = await res.text();
    statusOutput.textContent = text;
  } catch (err) {
    statusOutput.textContent = "Could not reach gateway: " + err.message;
  }
}

// ---------------------------------------------------------------------------
// Mode switching (Fan / League)
// ---------------------------------------------------------------------------
document.querySelectorAll(".mode-btn").forEach(btn => {
  btn.addEventListener("click", () => {
    document.querySelectorAll(".mode-btn").forEach(b => b.classList.remove("active"));
    document.querySelectorAll(".mode-panel").forEach(p => p.classList.remove("active"));
    btn.classList.add("active");
    document.getElementById(btn.dataset.mode + "-mode").classList.add("active");
  });
});

document.getElementById("refreshStatus").addEventListener("click", checkStatus);

// ---------------------------------------------------------------------------
// Form wiring — every <form data-op="..."> is handled generically.
// ---------------------------------------------------------------------------
document.querySelectorAll("form[data-op]").forEach(form => {
  form.addEventListener("submit", e => {
    e.preventDefault();
    const op = form.dataset.op;
    const formData = new FormData(form);
    const body = { operationType: op, leagueId: leagueSelect.value };

    for (const [key, rawValue] of formData.entries()) {
      if (key === "updateMode") continue; // handled separately below
      if (INT_FIELDS.has(key)) {
        body[key] = parseInt(rawValue, 10) || 0;
      } else if (FLOAT_FIELDS.has(key)) {
        body[key] = parseFloat(rawValue) || 0;
      } else {
        body[key] = rawValue;
      }
    }

    // updatePlayerStat: a value of 0 means "increment by 1"; any positive
    // value means "set directly" (see WorkerServer.dispatchRequest()).
    if (op === "updatePlayerStat") {
      const mode = formData.get("updateMode");
      body.value = mode === "increment" ? 0 : (parseInt(formData.get("value"), 10) || 1);
    }

    postRequest(body);
  });
});

// Default "year" inputs to the current year instead of being left blank/zero.
const currentYear = new Date().getFullYear();
document.querySelectorAll('input[name="year"]').forEach(input => {
  if (!input.value) input.value = currentYear;
});
