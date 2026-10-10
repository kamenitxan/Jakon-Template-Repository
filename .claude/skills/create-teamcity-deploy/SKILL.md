---
name: create-teamcity-deploy
description: Create a TeamCity project with Build + Deploy configurations for this Jakon project from scratch via the REST API (VCS root, sbt build, optional SonarQube, SSH deploy to /srv/<project>, restart of the systemd service). Use when the user wants to set up CI/CD, a TeamCity build or deploy, or "create deploy in TeamCity" for this project.
---

# Create TeamCity deploy

Creates a new TeamCity project for this repository via the REST API — no copying of an existing project is needed. Result:

- **Build** — VCS trigger on all branches; steps: clear coursier cache, `sbt ci`, optional SonarQube, `sbt assembly` + copy jar to `dist/`; artifacts: `dist/*.jar => jar`, `templates`, `static`, `jakon_config.properties`
- **Deploy** (type DEPLOYMENT, max 1 running) — triggered after a successful Build on the default branch; takes the Build artifacts and over SSH: removes old `templates`/`static`, uploads to `/srv/<project>`, fixes ownership, points `/srv/<project>/app.jar` at the newest jar, restarts the `<project>` service

This repository is a template: never write a concrete TeamCity URL, server, token, key name or project id into repository files.

**Secrets:** never ask for, type, or send passwords, tokens or private keys (no `secure:` properties in payloads). Authentication uses SSH keys already uploaded to TeamCity (referenced by name only); anything else the user fills in the TeamCity UI.

## 1. Connection

Uses the TeamCity REST API with a user access token:
- `TEAMCITY_URL` — e.g. `https://teamcity.example.com` (no trailing slash)
- `TEAMCITY_TOKEN` — TeamCity access token (Profile → Access Tokens), needs permission to create projects

Check both env vars exist **without printing them** (`[ -n "$TEAMCITY_URL" ] && [ -n "$TEAMCITY_TOKEN" ]`). If missing, ask the user to set them (user env vars, then restart the session). Never ask the user to paste the token into chat; never print it or put it in a URL, file or commit.

```bash
# JSON
curl -s -H "Authorization: Bearer $TEAMCITY_TOKEN" -H "Accept: application/json" "$TEAMCITY_URL/app/rest/..."
# create: -X POST -H "Content-Type: application/json" --data-binary @payload.json
# single value: -X PUT -H "Content-Type: text/plain" -H "Accept: text/plain" --data-binary @value.txt
```
Plain-text `PUT`s need **both** `Content-Type: text/plain` and `Accept: text/plain` (otherwise `406`). Write payloads to files in the scratchpad and send them with `--data-binary @file`.

Verify access: `GET /app/rest/users/current?fields=username,roles(role(roleId,scope))`.

## 2. Inputs

Read from the repo first, then ask for everything still missing in one question:

| input | source |
|---|---|
| `Name` | TeamCity display name; suggest from `projectName` in `build.sbt` |
| `NewId` | `<ParentId>_<NameWithoutSpaces>`; must not exist (`GET /app/rest/projects/id:<NewId>` → 404) |
| `ParentId` | ask; list with `GET /app/rest/projects?locator=count:1000&fields=project(id,name,parentProjectId)` (`_Root` = top level) |
| `project` | `projectName` in `build.sbt` (lowercase) — jar name `<project>-assembly-*.jar`, `/srv/<project>`, systemd service; must match the server provisioning (skill `create-project-on-server`) |
| repo URL | `git remote get-url origin`; ask if the repository is public or private |
| default branch | `git symbolic-ref refs/remotes/origin/HEAD` or ask (usually `master`/`main`) |
| deploy host / user | server address reachable from build agents and the SSH user (the `deploy_user` from `ansible/inventory.ini` if present) |
| SSH key name | name of a key uploaded in TeamCity (Project → SSH Keys, in the new project or any parent) whose public key is in `~deploy_user/.ssh/authorized_keys` on the server. There is no REST endpoint to list keys — suggest names already used by other configurations (`GET /app/rest/buildTypes?locator=count:1000&fields=buildType(id,steps(step(properties(property(name,value)))))`, values of `teamcitySshKey`) and ask the user |
| JDK | env parameter with the JDK home on agents: list candidates with `GET /app/rest/agents?locator=authorized:true&fields=agent(name,properties(property(name,value)))` and pick `env.JDK_<version>` matching the project's Java version; use as `%env.JDK_..%` |
| Sonar (optional) | `sonar-qube` project features in the parent chain: `GET /app/rest/projects/id:<id>/projectFeatures?locator=type:sonar-qube&fields=projectFeature(properties(property(name,value)))` — use property `id` as `sonarServer` (never print the `token` property); ask for `sonarProjectKey` or skip the step |

Derive build paths from `project/build.properties`:
- sbt **2.x**: classes `target/out/jvm/scala-*/<project>/classes`
- sbt **1.x**: classes `modules/backend/target/scala-*/classes`

The jar is located with `find` in the Assembly step, so its path does not depend on sbt or Scala version.

Show the user the full plan (ids, names, repo, branch, deploy host/user, key name, JDK, sonar) and **ask for confirmation before creating anything**.

## 3. Server prerequisites

The Deploy runs these over SSH as the deploy user — tell the user they must work without a password prompt (sudoers `NOPASSWD` for exactly these commands):
```
sudo /usr/bin/rm -rf /srv/<project>/templates /srv/<project>/static
sudo /usr/bin/chown -R <project>:<project> /srv/<project>
sudo /usr/bin/chmod -R g+w /srv/<project>/jar
sudo service <project> restart
```
and that `/srv/<project>` exists (skill `create-project-on-server`). Don't change the server yourself.

## 4. Create

Create in this order; after each request check the HTTP status and stop on errors. **Both build configurations are created with `"paused": true`** — they are unpaused only at the end.

### 4.1 Project
`POST /app/rest/projects`
```json
{"name":"<Name>","id":"<NewId>","parentProject":{"locator":"id:<ParentId>"}}
```

### 4.2 VCS root
`POST /app/rest/vcs-roots`
```json
{
  "id": "<NewId>_Repo",
  "name": "<repo URL>#refs/heads/<branch>",
  "vcsName": "jetbrains.git",
  "project": {"id": "<NewId>"},
  "properties": {"property": [
    {"name": "url", "value": "<repo URL>"},
    {"name": "branch", "value": "refs/heads/<branch>"},
    {"name": "teamcity:branchSpec", "value": "refs/heads/*"},
    {"name": "authMethod", "value": "ANONYMOUS"},
    {"name": "usernameStyle", "value": "USERID"},
    {"name": "submoduleCheckout", "value": "CHECKOUT"},
    {"name": "agentCleanPolicy", "value": "ON_BRANCH_CHANGE"},
    {"name": "agentCleanFilesPolicy", "value": "ALL_UNTRACKED"},
    {"name": "useAlternates", "value": "AUTO"}
  ]}
}
```
Private repository: either use the SSH URL (`git@github.com:owner/repo.git`) with `authMethod` = `TEAMCITY_SSH_KEY` and `{"name":"teamcitySshKey","value":"<key name with repo read access>"}`, or keep `ANONYMOUS` and tell the user to set credentials in the UI (VCS root → Authentication) before unpausing.

### 4.3 Build
`POST /app/rest/buildTypes`
```json
{
  "id": "<NewId>_Build", "name": "Build", "paused": true,
  "project": {"id": "<NewId>"},
  "settings": {"property": [
    {"name": "artifactRules", "value": "dist/*.jar => jar\nmodules/backend/templates => templates\nmodules/backend/static => static\nmodules/backend/jakon_config.properties"}
  ]},
  "vcs-root-entries": {"vcs-root-entry": [{"id": "<NewId>_Repo", "vcs-root": {"id": "<NewId>_Repo"}, "checkout-rules": ""}]},
  "steps": {"step": [
    {"name": "Clear sbt cache", "type": "simpleRunner", "properties": {"property": [
      {"name": "script.content", "value": "rm -rf /home/buildagent/.cache/coursier/v1"},
      {"name": "use.custom.script", "value": "true"}
    ]}},
    {"name": "SBT (ci)", "type": "simpleRunner", "properties": {"property": [
      {"name": "env.JAVA_HOME", "value": "%env.JDK_..%"},
      {"name": "env.SBT_OPTS", "value": "-Xmx512m -XX:ReservedCodeCacheSize=128m"},
      {"name": "script.content", "value": "<SBT_SCRIPT with command: ci>"},
      {"name": "use.custom.script", "value": "true"}
    ]}},
    {"name": "Assembly", "type": "simpleRunner", "properties": {"property": [
      {"name": "env.JAVA_HOME", "value": "%env.JDK_..%"},
      {"name": "env.SBT_OPTS", "value": "-Xmx512m -XX:ReservedCodeCacheSize=128m"},
      {"name": "script.content", "value": "<SBT_SCRIPT with command: assembly>\nrm -rf dist && mkdir dist\nfind . -path ./dist -prune -o -name '<project>-assembly-*.jar' -print -exec cp {} dist/ \\;\nls dist/*.jar"},
      {"name": "use.custom.script", "value": "true"}
    ]}}
  ]},
  "triggers": {"trigger": [
    {"type": "vcsTrigger", "properties": {"property": [
      {"name": "branchFilter", "value": "+:*"},
      {"name": "enableQueueOptimization", "value": "true"},
      {"name": "quietPeriodMode", "value": "DO_NOT_USE"}
    ]}}
  ]},
  "features": {"feature": [
    {"type": "perfmon", "properties": {"property": [{"name": "teamcity.perfmon.feature.enabled", "value": "true"}]}}
  ]}
}
```
`SBT_SCRIPT` (downloads the sbt launcher matching `project/build.properties`, no sbt needed on agents):
```bash
set -e
SBT_VERSION=$(grep '^sbt.version' project/build.properties | cut -d= -f2 | tr -d '[:space:]')
SBT_JAR="$HOME/.sbt/launchers/$SBT_VERSION/sbt-launch.jar"
if [ ! -f "$SBT_JAR" ]; then
	mkdir -p "$(dirname "$SBT_JAR")"
	curl -sL -o "$SBT_JAR" "https://repo1.maven.org/maven2/org/scala-sbt/sbt-launch/$SBT_VERSION/sbt-launch-$SBT_VERSION.jar"
fi
java $SBT_OPTS -jar "$SBT_JAR" <command>
```
Build the JSON with a JSON encoder (e.g. `python3 -c 'import json; ...'` or by writing the file carefully) so newlines, quotes, `$` and `\;` are escaped correctly.

Optional SonarQube step — insert between `SBT (ci)` and `Assembly`:
```json
{"name": "SonarQube", "type": "sonar-plugin", "properties": {"property": [
  {"name": "sonarServer", "value": "<sonar feature id>"},
  {"name": "sonarProjectKey", "value": "<key>"},
  {"name": "sonarProjectName", "value": "%system.teamcity.projectName%"},
  {"name": "sonarProjectVersion", "value": "%build.number%"},
  {"name": "sonarProjectSources", "value": "modules/backend/src"},
  {"name": "additionalParameters", "value": "-Dsonar.exclusions=modules/backend/src/main/resources/**,modules/backend/src/test/**\n-Dsonar.java.binaries=<classes path from step 2>"},
  {"name": "target.jdk.home", "value": "%env.JDK_..%"},
  {"name": "teamcity.tool.sonarquberunner", "value": "%teamcity.tool.sonar-qube-scanner.DEFAULT%"}
]}}
```
If the project produces a scoverage report, add `-Dsonar.scala.coverage.reportPaths=<path>/scoverage-report/scoverage.xml`.

### 4.4 Deploy
`POST /app/rest/buildTypes`. Every SSH step uses the same auth properties (`<AUTH>` below):
```json
{"name": "jetbrains.buildServer.deployer.username", "value": "<deploy user>"},
{"name": "jetbrains.buildServer.sshexec.authMethod", "value": "UPLOADED_KEY"},
{"name": "teamcitySshKey", "value": "<SSH key name>"}
```
```json
{
  "id": "<NewId>_Deploy", "name": "Deploy", "paused": true,
  "project": {"id": "<NewId>"},
  "settings": {"property": [
    {"name": "buildConfigurationType", "value": "DEPLOYMENT"},
    {"name": "maximumNumberOfBuilds", "value": "1"},
    {"name": "allowPersonalBuildTriggering", "value": "false"}
  ]},
  "steps": {"step": [
    {"name": "Remove old templates and static", "type": "ssh-exec-runner", "properties": {"property": [
      {"name": "jetbrains.buildServer.deployer.targetUrl", "value": "<host>"}, <AUTH>,
      {"name": "jetbrains.buildServer.sshexec.command", "value": "sudo /usr/bin/rm -rf /srv/<project>/templates /srv/<project>/static"}
    ]}},
    {"name": "Upload", "type": "ssh-deploy-runner", "properties": {"property": [
      {"name": "jetbrains.buildServer.deployer.targetUrl", "value": "<host>:/srv/<project>"}, <AUTH>,
      {"name": "jetbrains.buildServer.deployer.ssh.transport", "value": "jetbrains.buildServer.deployer.ssh.transport.scp"},
      {"name": "jetbrains.buildServer.deployer.sourcePath", "value": "jar/*.jar => jar\nstatic => static\ntemplates => templates\njakon_config.properties"},
      {"name": "teamcity.step.mode", "value": "execute_if_failed"}
    ]}},
    {"name": "Change group", "type": "ssh-exec-runner", "properties": {"property": [
      {"name": "jetbrains.buildServer.deployer.targetUrl", "value": "<host>"}, <AUTH>,
      {"name": "jetbrains.buildServer.sshexec.command", "value": "sudo /usr/bin/chown -R <project>:<project> /srv/<project>\nsudo /usr/bin/chmod -R g+w /srv/<project>/jar"}
    ]}},
    {"name": "Update symlink", "type": "ssh-exec-runner", "properties": {"property": [
      {"name": "jetbrains.buildServer.deployer.targetUrl", "value": "<host>"}, <AUTH>,
      {"name": "jetbrains.buildServer.sshexec.command", "value": "ln -sfn \"$(ls -t /srv/<project>/jar/<project>-assembly-*.jar | head -1)\" /srv/<project>/app.jar"}
    ]}},
    {"name": "Restart", "type": "ssh-exec-runner", "properties": {"property": [
      {"name": "jetbrains.buildServer.deployer.targetUrl", "value": "<host>"}, <AUTH>,
      {"name": "jetbrains.buildServer.sshexec.command", "value": "sudo service <project> restart"}
    ]}}
  ]},
  "triggers": {"trigger": [
    {"type": "buildDependencyTrigger", "properties": {"property": [
      {"name": "dependsOn", "value": "<NewId>_Build"},
      {"name": "afterSuccessfulBuildOnly", "value": "true"},
      {"name": "branchFilter", "value": "<branch>"}
    ]}}
  ]},
  "artifact-dependencies": {"artifact-dependency": [
    {"type": "artifact_dependency", "source-buildType": {"id": "<NewId>_Build"}, "properties": {"property": [
      {"name": "pathRules", "value": "jar/*.jar => jar\nstatic => static\ntemplates => templates\njakon_config.properties"},
      {"name": "cleanDestinationDirectory", "value": "true"},
      {"name": "revisionName", "value": "lastSuccessful"},
      {"name": "revisionValue", "value": "latest.lastSuccessful"}
    ]}}
  ]}
}
```

If any step fails, report what exists and offer to delete the half-created project (`DELETE /app/rest/projects/id:<NewId>`, only after the user agrees).

## 5. Verify

Read back both build types:
```
GET /app/rest/buildTypes/id:<bt>?fields=id,name,paused,settings(property(name,value)),steps(step(id,name,type,properties(property(name,value)))),triggers(trigger(id,type,properties(property(name,value)))),artifact-dependencies(artifact-dependency(id,source-buildType(id),properties(property(name,value)))),vcs-root-entries(vcs-root-entry(id))
```
Check: all steps present with the right properties, `<project>` everywhere (no leftover placeholders `<...>`), Deploy trigger and artifact dependency point to `<NewId>_Build`, VCS root URL = this repository, both configurations paused. Nothing queued: `GET /app/rest/buildQueue?locator=project:(id:<NewId>)&fields=count`.

## 6. Unpause and optional test build

Unpause (`PUT /app/rest/buildTypes/id:<bt>/paused`, text/plain `false`) only after the user confirms:
- the server is provisioned and the sudo commands from step 3 work without a password
- the SSH key is uploaded in TeamCity and authorized on the server (and repo credentials set for a private repo)
- `modules/backend/jakon_config.properties` in this repo has the right `port=` (CI uploads it to the server)

Once unpaused, a push triggers Build, and a successful Build on `<branch>` triggers Deploy. Offer to queue a Build first (ask): `POST /app/rest/buildQueue` with `{"buildType":{"id":"<NewId>_Build"},"branchName":"<branch>"}`.

## 7. Report

Link to the project (`$TEAMCITY_URL/project/<NewId>`), list the created configurations and their state (paused or not), and what the user still has to do in the UI or on the server.
