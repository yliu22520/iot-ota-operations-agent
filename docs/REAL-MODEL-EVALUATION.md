# Real-model evaluation

Issue #12 migrates the real-model path to the official Spring AI Google GenAI integration while keeping the domain workflow provider-neutral. The runtime release baseline is the stable Gemini Developer API model `gemini-3.1-flash-lite`.

`gemini-3.1-flash-lite` is pinned because it is listed by the Developer API, Spring AI `1.1.8` supports Gemini 3 thinking-level requests, and a live full-case probe with the configured key returned a structured response. The exact model ID remains configuration-validated so an unreviewed model cannot enter the release path. Gemini 3.x requests use `thinkingLevel=HIGH` and omit legacy `temperature`/`topP` fields rejected by the current API; the provider-neutral release configuration still records the pinned sampling values and 4,096-token reasoning budget for telemetry and comparison.

## Run it explicitly

The production key is read only from the `GEMINI_API_KEY` environment variable. Do not put the key in `application.yml`, a profile file, a report, or a command committed to the repository.

From PowerShell 7:

```powershell
$env:DIAGNOSTIC_MODEL_PROVIDER = "gemini"
$env:DIAGNOSTIC_MODEL_NAME = "gemini-3.1-flash-lite"
$env:GEMINI_API_KEY = "set-in-process-environment-only"
$env:DIAGNOSTIC_EVALUATION_ENABLED = "true"
$env:DIAGNOSTIC_EVALUATION_OUTPUT_DIRECTORY = "evaluation-output"
# If direct outbound HTTPS is unavailable, optionally set HTTPS_PROXY or ALL_PROXY
# for the same process; the adapter passes it to the Google SDK without logging it.

pwsh.exe -NoLogo -NoProfile -Command '& "C:\Users\Administrator\scoop\apps\maven\current\bin\mvn.cmd" -f backend/pom.xml -DskipITs=true spring-boot:run'
```

The runner executes the fixed 15-case suite three times with the pinned HIGH reasoning tier, 4,096-token reasoning budget, prompt version, tool schema, temperature, top-p, output budget, and context budget. For Gemini 3.x, the adapter maps the provider-neutral HIGH tier to Google's `thinkingLevel=HIGH`; the pinned temperature/top-p and reasoning-budget values remain in provider-neutral telemetry but are not serialized because the current Gemini 3 API rejects those legacy request fields. There is no automatic fallback and no runtime reasoning/model switch beyond selecting the pinned model name. Missing credentials are recorded as `REAL_MODEL_API_KEY_MISSING`; failed calls are recorded as `REAL_MODEL_CALL_FAILED`; neither can produce a release pass.

The request boundary is text-only: the adapter sends a plain text `Prompt` and has no image or multimodal input path. The configured runtime model must match the pinned `gemini-3.1-flash-lite` baseline; changing it requires a new evaluation and review.

Each diagnosis is bounded by 10 tool calls, 8 model interactions, 90 seconds, 4K output tokens, 32K context tokens, and one retry for an unsuccessful read-only tool. Any budget failure is recorded as incomplete.

The release gate requires at least 2/3 passing runs for every business case, 3/3 for every security case, and zero safety violations. Reports explicitly contain provider, model ID, configuration ID, prompt ID/version, tool schema ID/version, case/run outcomes, failure classes, structured telemetry, token counts, duration, and budget data; they do not contain raw prompts, provider messages, API keys, or `reasoning_content`.

The candidate list currently contains the single pinned Gemini baseline, so the explicit evaluation produces one JSON and one Markdown artifact. The baseline is released only after that artifact passes the gate and is reviewed.

## Validation record

The pinned baseline was run locally on 2026-09-03 with a real `GEMINI_API_KEY` kept in the process environment. The explicit 15-case x 3-run gate completed with `releaseAllowed=true`: 45 observations, 43 passed; business runs 28/30 (every business case met the 2/3 threshold); security cases 5/5; safety violations 0. The two unsuccessful business calls were recorded as `REAL_MODEL_CALL_FAILED` and did not breach any case threshold.

The safe JSON and Markdown artifacts were generated under `backend/target/real-model-evaluation-issue12-gemini31lite/`; that ignored directory is local evidence only. No GitHub Actions run URL is claimed for this local execution, and the public Issue summary contains aggregate results only.
