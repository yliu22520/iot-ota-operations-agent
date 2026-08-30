# Real-model evaluation

Issue #12 migrates the real-model path to the official Spring AI Google GenAI integration while keeping the domain workflow provider-neutral. The runtime release baseline is the stable Gemini Developer API model `gemini-2.5-flash`.

`gemini-2.5-flash` is pinned because the Spring AI `1.1.0` line provides the official `spring-ai-starter-model-google-genai` integration and Google's model documentation lists this Flash model as stable with a free tier. The exact model ID remains configuration-validated so an unreviewed model cannot enter the release path.

## Run it explicitly

The production key is read only from the `GEMINI_API_KEY` environment variable. Do not put the key in `application.yml`, a profile file, a report, or a command committed to the repository.

From PowerShell 7:

```powershell
$env:DIAGNOSTIC_MODEL_PROVIDER = "gemini"
$env:DIAGNOSTIC_MODEL_NAME = "gemini-2.5-flash"
$env:GEMINI_API_KEY = "set-in-process-environment-only"
$env:DIAGNOSTIC_EVALUATION_ENABLED = "true"
$env:DIAGNOSTIC_EVALUATION_OUTPUT_DIRECTORY = "evaluation-output"

pwsh.exe -NoLogo -NoProfile -Command '& "C:\Users\Administrator\scoop\apps\maven\current\bin\mvn.cmd" -f backend/pom.xml -DskipITs=true spring-boot:run'
```

The runner executes the fixed 15-case suite three times with the pinned HIGH reasoning tier, 4,096-token reasoning budget, prompt version, tool schema, temperature, top-p, output budget, and context budget. For Gemini 2.5, the adapter maps the provider-neutral reasoning budget to Google's `thinkingBudget`; there is no automatic fallback and no runtime reasoning/model switch beyond selecting the pinned model name. Missing credentials are recorded as `REAL_MODEL_API_KEY_MISSING`; failed calls are recorded as `REAL_MODEL_CALL_FAILED`; neither can produce a release pass.

The request boundary is text-only: the adapter sends a plain text `Prompt` and has no image or multimodal input path. The configured runtime model must match the pinned `gemini-2.5-flash` baseline; changing it requires a new evaluation and review.

Each diagnosis is bounded by 10 tool calls, 8 model interactions, 90 seconds, 4K output tokens, 32K context tokens, and one retry for an unsuccessful read-only tool. Any budget failure is recorded as incomplete.

The release gate requires at least 2/3 passing runs for every business case, 3/3 for every security case, and zero safety violations. Reports explicitly contain provider, model ID, configuration ID, prompt ID/version, tool schema ID/version, case/run outcomes, failure classes, structured telemetry, token counts, duration, and budget data; they do not contain raw prompts, provider messages, API keys, or `reasoning_content`.

The candidate list currently contains the single pinned Gemini baseline, so the explicit evaluation produces one JSON and one Markdown artifact. The baseline is released only after that artifact passes the gate and is reviewed.
