# Connect GitHub with your own GitHub App

Bootshift talks to GitHub as a **GitHub App that you create and own**. Each install gets its own
app, and the app only sees the repositories you install it on (README sections 13 and 18).

## Connect GitHub (recommended)

1. Open **Settings** (or **Repositories**) in the dashboard and click **Connect GitHub**. To create
   the app under an organization you own, click **For an organization** first and enter its name.
2. GitHub opens with the app already filled in. Check it and click **Create GitHub App**.
3. GitHub sends you back to Bootshift, which saves the app and forwards you to its install page.
   Choose **Only select repositories** and pick the ones Bootshift may see.
4. GitHub sends you to **Repositories**. Turn on the switch for each repository Bootshift may
   migrate.

The app Bootshift describes is:

| Setting | Value |
|---|---|
| Name | `bootshift-` and six random hex characters (you can change it on GitHub) |
| Visibility | Private: only installable on the account that created it |
| Repository permissions | **Contents:** read and write · **Pull requests:** read and write · **Metadata:** read |
| Webhook | Off. Bootshift does not use webhooks yet |
| Setup URL | `<dashboard>/repositories?installed=1`, also after you change the installation |

### Where the key goes

GitHub hands the app's private key to the backend once, when it trades the one-hour code from the
redirect. The backend writes two files to `~/.bootshift` (on Windows `C:\Users\<you>\.bootshift`),
or `BOOTSHIFT_HOME` if set:

- `github-app.json`: the App ID, slug and links. No secrets.
- `github-app-<id>.pem`: the private key.

Both are readable only by the user running Bootshift (`rw-------` on Linux and macOS, an
owner-only ACL on Windows). The key is never stored in the database, sent to the dashboard or
logged. GitHub's client secret and webhook secret are not kept at all.

**Disconnect** in Settings deletes both files. The app itself stays on GitHub until you delete it
from its settings page (**View the app on GitHub** links there).

### Safety checks

- Each flow carries a random, single-use `state` that expires after an hour, so a redirect that
  Bootshift did not start is refused.
- Without `BOOTSHIFT_DASHBOARD_URL`, the button only works from a `localhost`, `127.0.0.1` or
  `[::1]` dashboard. A server reached by another name must set `BOOTSHIFT_DASHBOARD_URL` to the
  address the browser uses, for example `https://bootshift.example.org`.
- An app set with the variables below cannot be replaced or removed from the dashboard.

## Set it up by hand

Use this when the backend cannot be reached from your browser during setup, or when you manage
secrets elsewhere (Docker secrets, a vault). Variables win over an app saved by the button.

### 1. Create the app

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

### 2. Generate a private key

On the app page, click **Generate a private key**. A `.pem` file downloads.

- Keep it **outside the repository**, for example `~/.bootshift/github-app.pem`
  (on Windows, `C:\Users\<you>\.bootshift\github-app.pem`).
- Never commit it, paste it into an issue or chat, or put it in the frontend. It lets anyone act
  as your app on every repository it is installed on.
- If it leaks, delete it on the app page and generate a new one.

### 3. Start the backend with the app settings

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

The **Settings** page should now show *Connected*. Install the app and allow repositories as in
steps 3 and 4 above.

## Allowlist

Once a GitHub App is connected, **runs are refused for repositories that are not allowed**.
Removing a repository from the allowlist keeps its past runs.

## How it works

- The backend signs a JWT with the private key (RS256, valid nine minutes) to call app endpoints.
- It exchanges that for an **installation token** (valid one hour, cached until five minutes
  before expiry) to list repositories. Tokens never leave the backend.
- Clones use a separate token limited to one repository with `contents: read`. It is not cached
  and never reaches the sandbox (README section 13).
- GitHub's PKCS#1 keys (`BEGIN RSA PRIVATE KEY`) and PKCS#8 keys (`BEGIN PRIVATE KEY`) both work.

## Not yet

Pushing the `bootshift/spring-boot-4` branch and opening pull requests arrive in Phase 5. Until
then, runs clone and build allowed repositories for real, and the later stages are simulated.
