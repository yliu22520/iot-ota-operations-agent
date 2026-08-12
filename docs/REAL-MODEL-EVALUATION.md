# Real-model evaluation

Issue #8 adds an explicit DeepSeek evaluation runner. It compares both pinned candidates with the same 15-case suite, runs every case three times, and writes one JSON plus one Markdown artifact per candidate.

## Run it explicitly

The production key is read only from the `DEEPSEEK_API_KEY` environment variable. Do not put the key in `application.yml`, a profile file, a report, or a command committed to the repository.

From PowerShell 7:

```powershell
$env:DIAGNOSTIC_MODEL_PROVIDER = "deepseek"
$env:DIAGNOSTIC_EVALUATION_ENABLED = "true"
$env:DIAGNOSTIC_EVALUATION_OUTPUT_DIRECTORY = "evaluation-output"

pwsh.exe -NoLogo -NoProfile -Command '& "C:\Users\Administrator\scoop\apps\maven\current\bin\mvn.cmd" -f backend/pom.xml -DskipITs=true spring-boot:run'
```

The runner calls `deepseek-v4-flash` and `deepseek-v4-pro` with the same HIGH reasoning tier, prompt version, tool schema, temperature, and top-p. It does not auto-fallback or expose a runtime model/reasoning switch. Missing credentials or a failed real call is recorded as `REAL_MODEL_UNAVAILABLE`/`REAL_MODEL_CALL_FAILED`, and cannot produce a release pass.

The current runtime release is pinned to `deepseek-v4-flash`. Its request boundary is text-only: the adapter sends a plain text `Prompt` and has no image or multimodal input path. The `deepseek-v4-pro` configuration is retained only for the explicit pre-release comparison run.

Each diagnosis is bounded by 10 tool calls, 8 model interactions, 90 seconds, 4K output tokens, 32K context tokens, and one retry for an unsuccessful read-only tool. Any budget failure is recorded as incomplete.

The release gate requires at least 2/3 passing runs for every business case, 3/3 for every security case, and zero safety violations. Reports contain configuration IDs, case/run outcomes, failure classes, structured telemetry, token counts, and duration; they do not contain raw prompts, provider messages, API keys, or `reasoning_content`.

The baseline is selected only after both candidate artifacts are reviewed. The default release configuration remains `deepseek-v4-flash`; changing it requires a new evaluation and review.
