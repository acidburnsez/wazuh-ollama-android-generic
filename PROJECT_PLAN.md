# SecOps Mobile: Wazuh & Ollama Android Dashboard
## Project Plan & Architecture Design

SecOps Mobile is a native Android application designed to give system administrators real-time visibility into security alerts from **Wazuh SIEM/XDR** and integrate with **Ollama AI** for on-the-fly alert threat analysis and step-by-step remediation advice.

---

## 1. Architecture Overview

```mermaid
graph TD
    subgraph Mobile Device
        App[SecOps Mobile Android App]
        LocalCache[(Room DB / EncryptedSharedPreferences)]
    end

    subgraph Home Lab Server (Proxmox Virtual Environment)
        Caddy[Caddy Reverse Proxy: wazuh.example.com]
        Wazuh[Wazuh SIEM Manager: CT 310]
        Ollama[Ollama AI Engine: CT 311]
        Gotify[Gotify Notification Server: CT 307]
    end

    subgraph Development & Agent Execution
        GitHub[GitHub Repo: yourgithubusername/proxmox]
        Agent[Antigravity AI Agent]
    end

    App -->|HTTPS / REST API| Caddy
    Caddy -->|Reverse Proxy| Wazuh
    App -->|HTTPS / REST API| Ollama
    App -->|WebSocket / Push| Gotify
    App -->|Create Issue| GitHub
    Agent -->|Read Issue & Execute| GitHub
    App -.->|Internal Storage| LocalCache
```

### Technical Stack
* **Language:** Kotlin
* **UI Framework:** Jetpack Compose (Material Design 3)
* **Architecture Pattern:** MVVM (Model-View-ViewModel) with Clean Architecture principles
* **Networking:** Retrofit + OkHttp (with custom SSL pinning/trust stores)
* **Push Notifications:** Gotify SDK / WebSocket client (Privacy-first, self-hosted push notifications)
* **Asynchronous Execution:** Kotlin Coroutines & Flows
* **Local Storage:** Room Database (for caching alerts) + EncryptedSharedPreferences (for API keys/SSO tokens)

---

## 2. Core Functional Requirements

### 📊 Real-Time Security Dashboard
* **Alert Summaries:** Visual gauge and graphs showing alert frequencies grouped by Wazuh levels (e.g., Level 12+ Critical, Level 8-11 Warning, Level <8 Info).
* **Agent Status Monitor:** Overview of active, disconnected, and never-active Wazuh agents across the homelab stack.
* **Alert Feed:** A chronologically sorted stream of Wazuh alerts featuring searching, filtering (by agent ID, severity, or rule description), and pinning capabilities.

### 🔔 Push Notifications
* **Threshold-Based Alerts:** Background service listening to Gotify notifications or polling the Wazuh socket. Triggers Android system push notifications for alerts exceeding a configured threshold (e.g., Level 12+).
* **High-Priority Channel:** System notifications configured with critical sounds and vibration patterns for immediate visibility.

### 🤖 Ollama AI Analysis
* **Contextual Explanations:** A single-tap action on any alert to fetch the full JSON log, bundle it into a security context prompt, and send it to **Ollama** (`llama3` or `mistral`).
* **Instant Explanations:** The AI parses complex syslog dumps or AppArmor denials into standard language explaining *what* happened.
* **Remediation Checklist:** Generates three actionable, specific commands or remediation steps to address the threat.

### ⚡ Antigravity Remediation Bridge
* **"Dispatch to Antigravity" Trigger:** A button in the Alert Details view that formats the alert details, the AI's impact assessment, and the target machine's system specs.
* **GitHub Issue Integration:** Submits the remediation package directly to your `proxmox` repository as a new GitHub issue with the tag `[remediation-request]`.
* **Agent Execution:** The Antigravity agent automatically detects the new issue, reviews the required remediation actions, writes/executes the fix, and closes the issue with a status summary!

### 🔒 Enterprise Security
* **Encrypted Token Storage:** Credentials, Wazuh Auth tokens, and Tailscale connection keys stored using Android Keystore System.
* **SSO Integration:** Supporting authentication via **Authelia** (OAuth2/OIDC) to mirror the web stack login profile.
* **LAN/WireGuard Check:** Automatically checks if the host is reachable locally or if a connection via Tailscale/WireGuard is required.

---

## 3. UI/UX Flow Specification

### 1. Splash & Auth Screen
* Glassmorphic backdrop styling with smooth fade-in animations.
* Authelia SSO Web Authentication fallback.
* Host connectivity checks.

### 2. Main Dashboard (Navigation Tab 1)
* Grid layout containing Material 3 cards for stats (Critical count, Warn count, Active Agents, Host Memory/CPU).
* Interactive Sparklines/Bar charts showing alert events over the last 24 hours.

### 3. Live Feed (Navigation Tab 2)
* Color-coded alert list items (Red indicator for level 12+, Orange for level 8-11, Blue for info).
* Filter chips at the top: `Critical`, `Warnings`, `System`, `Audit`.
* Search bar dynamically filtering lists.

### 4. Alert Details Screen (Detail View)
* Structured key-value grid for alert metrics (Rule ID, description, agent hostname, timestamp).
* Expandable section for the raw JSON log dump.
* Action Bar:
  * **Ask Ollama:** Opens slide-up sheet showing AI threat explanations.
  * **Remediate:** Opens dispatch confirmation dialog to invoke Antigravity.

---

## 4. API Integrations

### A. Wazuh Manager API (`https://wazuh.example.com/api`)
1. **Authentication:** `GET /security/user/authenticate` -> Returns a JSON Web Token (JWT).
2. **Alert Stats:** `GET /alerts/stats` -> Returns aggregated counts of levels.
3. **Alert List:** `GET /alerts` -> Returns chronologically filtered lists of alerts matching filters.
4. **Agent List:** `GET /agents` -> Returns current connection states.

### B. Ollama API (`https://ollama.example.com/api`)
1. **Chat Completion:** `POST /api/chat` -> Connects to local LLM models (e.g. `llama3`) using streamable responses.
   * **Prompt Structure:**
     ```
     You are Antigravity-SIEM, an agentic Security Engineer. Explain the following Wazuh alert:
     Alert Rule: [Rule ID] - [Description]
     Log Data: [Raw Log / Syslog]
     
     Provide:
     1. Brief threat assessment (is it a false positive?)
     2. Potential impact
     3. 3 specific terminal commands or actions to remediate it.
     ```

### C. Gotify Server API (`https://gotify.example.com`)
1. **WebSocket client:** Connecting to `ws://gotify.example.com/stream?token=<client_token>` to receive real-time push events.
2. **Message parser:** Extracts alert levels and maps them to high-priority Android notification channels.

### D. GitHub API (`https://api.github.com/repos/yourgithubusername/proxmox/issues`)
1. **Create Issue:** `POST /issues`
   * **Body:**
     ```json
     {
       "title": "[Remediation Request] Wazuh Rule {rule_id}: {description}",
       "body": "### Alert Details\n- **Agent:** {agent_name}\n- **IP:** {agent_ip}\n- **Rule:** {rule_id}\n- **Timestamp:** {time}\n\n### Raw Log\n```json\n{raw_log}\n```\n\n### Ollama Threat Assessment\n{ollama_analysis}",
       "labels": ["remediation-request"]
     }
     ```

---

## 5. Implementation Roadmap

### Phase 1: Bootstrap & Project Setup 
* [x] Initialize Android Studio project with Kotlin, Gradle (Version Catalog), Jetpack Compose.
* [x] Implement Material Design 3 theme system (Support sleek dark/night mode with glassmorphic accents).
* [x] Configure Android manifest permissions (Internet, Network State).
* [ ] Set up Hilt Dependency Injection.

### Phase 2: Authentication & Connection Manager 
* [ ] Create server configuration screen (Wazuh Host Url, API token / Authelia login credentials, Ollama API Url).
* [ ] Implement secure credential storage using Android Keystore.
* [ ] Implement JWT auto-refresh interceptors in OkHttp.

### Phase 3: Wazuh Dashboards & Feeds 
* [ ] Design UI screens for home dashboard utilizing Compose Canvas (custom graphs, metrics gauges).
* [ ] Develop the scrollable Alert Feed with pull-to-refresh.
* [ ] Integrate Room Database for offline alert caching.

### Phase 4: Push Notifications & Remediations 
* [ ] Integrate Gotify WebSocket receiver service to push background system alerts.
* [ ] Implement the GitHub API endpoint for issue creation/remediation requests.
* [ ] Integrate Ollama `/api/chat` with SSE (Server-Sent Events) or JSON streams.
* [ ] Construct a fluid, type-writer effect response panel in Compose.

### Phase 5: Hardening & Testing 
* [ ] Conduct unit tests for viewmodels and API response parsing.
* [ ] Ensure network security config allows self-signed/intranet certs (if Tailscale is used directly).
* [ ] Build release APK and bundle.
