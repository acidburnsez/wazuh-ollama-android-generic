# SecOps Mobile: Wazuh & Ollama Android Dashboard

SecOps Mobile is a native Android application designed to give system administrators real-time visibility into security alerts from **Wazuh SIEM/XDR** and integrate with **Ollama AI** for on-the-fly alert threat analysis and step-by-step remediation advice.

---

## 🚀 Key Features (Unified Homelab Command Center)

* **Overview Cockpit:** Real-time host metrics (CPU, RAM, Disk) queried directly from Prometheus, alongside active ping checkers for all homelab services (Caddy, Authelia, Gitea, Grafana, Plex, etc.).
* **Infrastructure Management:** Lists VM/LXC container states on Proxmox VE, letting you execute Start, Reboot, and Stop commands directly from the dashboard.
* **Portal Quick-Launcher:** Displays an interactive grid of shortcuts that launch pre-authenticated in-app WebViews (retaining your Authelia SSO session cookie) for seamless navigation.
* **Real-Time Security Feed:** Streams active Wazuh alerts with robust search filters (rule level, source agent, rule descriptions).
* **AI Security Analyst:** Send any wazuh alert directly to your local Ollama LLM container (`llama3`) for contextual offline threat assessments and instant remediation advice.
* **Secure Storage:** Uses Android Keystore System to encrypt and store Wazuh REST tokens, Authelia cookie hashes, and Proxmox authorization tokens locally.

---

## 🛠️ Architecture

* **UI:** Kotlin + Jetpack Compose (Material Design 3)
* **Architecture:** MVVM + Clean Architecture + Repository Pattern
* **Local Storage:** Room Database + EncryptedSharedPreferences
* **Networking:** Retrofit + OkHttp
* **Concurrency:** Kotlin Coroutines + Flow

---

## 📂 Getting Started

1. **Clone the Repo:**
   ```bash
   git clone ssh://git@192.168.11.69:222/user/wazuh-ollama-android.git
   cd wazuh-ollama-android
   ```
2. **Review the Project Plan:**
   Read [PROJECT_PLAN.md](PROJECT_PLAN.md) to understand the system architecture, API specifications, and development roadmap.
3. **Open in Android Studio:**
   Import the project folder. Ensure you have JDK 17+ and the Android SDK configured.

---

## ⚙️ CI/CD Pipelines

This repository is integrated with **Gitea Actions** for automated build checks:
* **Workflow Configuration:** Pre-configured in `.gitea/workflows/android.yaml`.
* **Execution:** On every push to `main`, the self-hosted `gitea-runner` container spins up a `gradle:8.5-jdk17` build container, resolves dependencies, and compiles the project.
* **Artifacts:** Pushing builds automatically uploads the resulting `app-debug.apk` directly to Gitea's build logs interface for immediate download and installation.
