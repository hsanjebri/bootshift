# Connect GitHub with your own GitHub App

Bootshift talks to GitHub as a **GitHub App that you create and own**. Each install gets its own
app, and the app only sees the repositories you install it on (README sections 13 and 18).

## 1. Create the app

Open <https://github.com/settings/apps/new> (or your organization's *Developer settings*) and set:

| Field | Value |
|---|---|
| GitHub App name | Anything unique, for example `bootshift-<you>` |
| Homepage URL | Your Bootshift URL, or the project's GitHub page |
| Setup URL (Post installation) | `http://localhost:4321/repositories?installed=1` and tick **Redirect on update** |
| Webhook | Untick **Active**. Bootshift does not use webhooks yet |
| Repository permissions | **Contents:** Read and write · **Pull requests:** Read and write · **Metadata:** Read-only |
| Where can this GitHub App be installed? | **Only on this account** |

Click **Create GitHub App**. Note the **App ID** and the app's URL name, the `slug` in
`https://github.com/apps/<slug>`.

## 2. Generate a private key

On the app page, click **Generate a private key**. A `.pem` file downloads.

- Keep it **outside the repository**, for example `~/.bootshift/github-app.pem`
  (on Windows, `C:\Users\<you>\.bootshift\github-app.pem`).
- Never commit it, paste it into an issue or chat, or put it in the frontend. It lets anyone act
  as your app on every repository it is installed on.
- If it leaks, delete it on the app page and generate a new one.

## 3. Start the backend with the app settings

```bash
export GITHUB_APP_ID=123456
export GITHUB_APP_SLUG=bootshift-you
export GITHUB_APP_PRIVATE_KEY_PATH=~/.bootshift/github-app.pem
cd backend && ./mvnw spring-boot:run
```

On Windows PowerShell:

```powershell
$env:GITHUB_APP_ID = "123456"
$env:GITHUB_APP_SLUG = "bootshift-you"
$env:GITHUB_APP_PRIVATE_KEY_PATH = "$HOME\.bootshift\github-app.pem"
cd backend; .\mvnw.cmd spring-boot:run
```

The **Settings** page should now show *Connected*.

## 4. Install the app and allow repositories

1. Click **Install on GitHub** in Settings (or open `https://github.com/apps/<slug>/installations/new`).
2. Choose **Only select repositories** and pick the ones Bootshift may see.
3. GitHub redirects back to **Repositories**. Turn on the switch for each repository Bootshift may
   migrate.

Once a GitHub App is configured, **runs are refused for repositories that are not allowed**.
Removing a repository from the allowlist keeps its past runs.

## How it works

- The backend signs a JWT with the private key (RS256, valid nine minutes) to call app endpoints.
- It exchanges that for an **installation token** (valid one hour, cached until five minutes
  before expiry) to list repositories. Tokens never leave the backend.
- GitHub's PKCS#1 keys (`BEGIN RSA PRIVATE KEY`) and PKCS#8 keys (`BEGIN PRIVATE KEY`) both work.

## Not yet

Cloning, pushing the `bootshift/spring-boot-4` branch and opening pull requests arrive with the
real pipeline (Phases 2 to 5). Until then, runs on allowed repositories are still simulated.
