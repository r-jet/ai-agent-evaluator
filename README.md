# Sprinklr AI Agent E2E Evaluator

An end-to-end conversational evaluation system for testing AI customer support agents built on Sprinklr. The system simulates realistic customer conversations using an LLM, scores the AI agent's responses against structured criteria, and produces timestamped reports — all without any human involvement.

---

## Why I built this

AI agents in customer support are only as good as their ability to handle real, messy conversations — not just scripted demos. Standard unit tests can't capture this. I built this system to answer a specific question:

> *"How do we know the AI agent actually behaves well across a range of real customer scenarios?"*

The answer is an automated evaluation loop where one LLM acts as the customer, another scores the agent, and the results are structured enough to track over time.

---

## How it works

```
┌─────────────────────────────────────────────────────────────────┐
│                        Test Suite Runner                         │
│                                                                  │
│   ┌──────────────┐     HTTP      ┌──────────────────────────┐   │
│   │  Customer    │  ──────────►  │   Sprinklr Live Chat     │   │
│   │  LLM         │               │   (AI Agent under test)  │   │
│   │  (OpenAI)    │  ◄──────────  │                          │   │
│   └──────────────┘               └──────────────────────────┘   │
│          │                                                        │
│          │  Full transcript                                       │
│          ▼                                                        │
│   ┌──────────────┐                                               │
│   │  Evaluator   │  Scores agent on per-scenario criteria        │
│   │  LLM (GPT-4o)│  Returns structured pass/fail + summary      │
│   └──────────────┘                                               │
│          │                                                        │
│          ▼                                                        │
│   ┌──────────────┐                                               │
│   │  Reports     │  .txt (human-readable) + .json (parseable)   │
│   └──────────────┘                                               │
└─────────────────────────────────────────────────────────────────┘
```

**The conversation loop:**
1. A `TestScenario` defines the customer persona, their problem, max turns, and evaluation criteria
2. The customer LLM (GPT-4o-mini) generates realistic messages based on the persona
3. Messages are sent to the Sprinklr AI agent via Live Chat APIs
4. Agent replies are polled and fed back to the customer LLM to continue the conversation
5. After all turns complete, a separate evaluator LLM (GPT-4o) judges the full transcript against the criteria
6. Results are written to timestamped `.txt` and `.json` report files

---

## Key design decisions

**Two-LLM architecture**
The customer LLM and the evaluator LLM are separate calls with different system prompts and different models. This separation matters: using the same model instance for both would introduce bias, and using the same model at all risks circular evaluation. The evaluator uses a stronger model (GPT-4o) at low temperature (0.1) for consistent, deterministic scoring.

**Timestamp-based reply detection (not cursor-based)**
Early versions used API cursors to detect new agent messages. This caused a race condition — Sprinklr's indexing delay meant cursors snapshotted *before* the customer message was indexed, causing duplicate sends. The fix: `sendMessage()` records the server-assigned `creationTime` from the send response, and `fetchNewAgentReply()` only returns agent messages with a timestamp strictly greater than that value. Race-condition immune.

**Scenario-driven, not hardcoded**
Test scenarios are defined as plain data (`TestScenario` records) and loaded either from code or from an Excel file. A non-technical QA person can add new scenarios by filling in a spreadsheet — no code changes required.

**Structured evaluation output**
The evaluator returns JSON with per-criterion pass/fail results, an overall score, a summary, and a concerns field. This makes results parseable for dashboards or CI gates, not just human-readable logs.

---

## Sample evaluation output

```
════════════════════════════════════════════════════════════
EVALUATION: slow-internet-peak-hours
Overall Score: 40/100
Criteria: 2/5 passed
────────────────────────────────────────────────────────────
  ✅ Agent did not provide a link to a page unrelated to the question
  ✅ Agent's response was factually accurate regarding the service plan
  ❌ Agent correctly explained the difference between throttling and congestion
     → The agent mentioned congestion but did not clearly explain the distinction
  ❌ Agent confirmed whether the customer's plan includes any throttling policy
     → No confirmation of throttling policy was provided
  ❌ Agent offered at least one concrete next step or upgrade option
     → No concrete steps or upgrade options were offered
────────────────────────────────────────────────────────────
Summary: The agent failed to explain throttling vs congestion and provided
no concrete resolution path. Factual accuracy was maintained but helpfulness
was low.
Concerns: Agent repeated its opening greeting on turns 2 and 3, which
suggests a context retention issue in the underlying agent configuration.
════════════════════════════════════════════════════════════
```

---

## Project structure

```
src/main/java/com/rajat/aie2e/
├── Main.java                        Entry point, scenario source selection
├── config/
│   └── AppConfig.java               All config from env vars, with console prompts
├── scenarios/
│   ├── TestScenario.java            Record: name, persona, criteria, maxTurns
│   ├── ScenarioRegistry.java        Built-in scenario definitions
│   └── ScenarioLoader.java          Parses scenarios from Excel (.xlsx)
├── livechat/
│   ├── LiveChatClient.java          Sprinklr API calls (handshake, send, fetch, close)
│   ├── LiveChatException.java       Typed exception for API failures
│   └── RequestLogger.java           Logs every HTTP call as a curl equivalent
├── customer/
│   └── CustomerLLMClient.java       Stateless OpenAI client for customer persona
├── evaluation/
│   ├── EvaluatorClient.java         Judge LLM — scores transcripts against criteria
│   └── EvaluationResult.java        Structured result record with display helpers
├── runner/
│   └── ConversationRunner.java      Orchestrates one scenario end-to-end
└── reporting/
    ├── ConversationLogger.java       Per-scenario .txt + .json logs with timestamps
    └── ReportGenerator.java          Summary report across all scenarios in a run
```

---

## Running it

**Prerequisites**
- Java 21+
- Maven
- An OpenAI API key
- Access to a Sprinklr Live Chat application

**Setup**

```bash
git clone https://github.com/rajat-lunawat/ai-agent-evaluator.git
cd ai-agent-evaluator
```

Set your API key as an environment variable:
```bash
export OPENAI_API_KEY=sk-...
```

Or in IntelliJ: **Run → Edit Configurations → Environment Variables**

**Run**
```bash
mvn compile exec:java -Dexec.mainClass=com.rajat.aie2e.Main
```

At startup you'll be prompted for:
1. Your Sprinklr Live Chat Application ID
2. Scenario source — built-in scenarios or an Excel file (a file picker dialog opens)

**Reports** are written to `reports/` after each run:
- `reports/<scenario-name>_<timestamp>.txt` — full transcript + evaluation
- `reports/<scenario-name>_<timestamp>.json` — structured results
- `reports/summary_<timestamp>.txt` — cross-scenario comparison

---

## Adding new test scenarios

**Option 1 — Excel (recommended for non-developers)**

Fill in a row per scenario in the provided template (`Test_Scenarios_Template.xlsx`):

| Column | Description |
|---|---|
| Name | Short unique ID, used in filenames |
| Description | One-line summary of the scenario |
| CustomerPersona | System prompt defining who the customer is and their situation |
| OpeningPrompt | Instruction for the first message the customer sends |
| MaxTurns | How many back-and-forth turns to run |
| Evaluation Criteria | One criterion per line (Alt+Enter), pipe-separated, or comma-separated |

**Option 2 — Code**

Add a `TestScenario` record to `ScenarioRegistry.java` and include it in `ALL_SCENARIOS`.

---

## Tech stack

| Component | Technology |
|---|---|
| Language | Java 21 |
| HTTP client | Java built-in `java.net.http.HttpClient` |
| JSON parsing | Jackson |
| Excel parsing | Apache POI |
| Customer LLM | OpenAI GPT-4o-mini |
| Evaluator LLM | OpenAI GPT-4o |
| Agent under test | Sprinklr Live Chat API |
| IDE | IntelliJ IDEA |

---

## What this demonstrates

- **LLM evaluation design** — how to structure an LLM-as-judge system with per-criterion scoring, separation of customer and evaluator models, and structured JSON output
- **Prompt engineering** — customer personas are crafted to produce realistic, varied behaviour; the evaluator prompt is designed for consistency at low temperature
- **API integration** — working with a real commercial AI platform (Sprinklr) via REST APIs, handling polling, auth tokens, and transient errors
- **Scalable test design** — scenarios as data, not code; non-technical users can add scenarios via Excel without touching the codebase