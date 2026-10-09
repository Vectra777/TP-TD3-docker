# TP DevOps Correction Docker

Correction de la partie Docker du module DevOps. Amusez-vous bien avec GitHub Actions !


## First CI with backend tests

The [CI/CD entry point](.github/workflows/main.yml) runs on pushes to `main`
and `develop`, and on pull requests targeting either branch. Its reusable
[backend workflow](.github/workflows/test-backend.yml) checks out the repository, sets up Temurin JDK 21
on Ubuntu 24.04, and caches Maven dependencies using `simple-api/pom.xml` as
the cache dependency file. The workflow only requests read access to repository
contents.

The build runs `mvn -B clean verify` inside `simple-api`. Surefire runs unit
tests and Failsafe runs integration tests; a failure in either suite fails CI.
Testcontainers starts a temporary PostgreSQL database using the runner's Docker
daemon. Version 1.21.4 supports recent Docker APIs, fixing the startup failure
caused by the old client's API version 1.32.

To run the same checks locally, install JDK 21 and Maven, start Docker, then run:

```sh
cd simple-api
mvn -B clean verify
```

Results are available in the repository's Actions tab. Local test reports are
written to `simple-api/target/surefire-reports` and
`simple-api/target/failsafe-reports`.



## Continuous delivery and split workflows

This repository uses `main` where the exercise says `master`.

| Event | Backend and frontend tests | SonarCloud gate | Docker Hub publication |
| --- | --- | --- | --- |
| Push to `main` | Yes | Required | Only after tests and gate pass |
| Push to `develop` | Yes | Required | No |
| Same-repository PR to `main` or `develop` | Yes | Required | No |
| Fork or Dependabot PR to `main` or `develop` | Yes | Unavailable without secrets | No |

The entry point exposes five jobs: `test-backend`, `test-frontend`,
`publish-backend`, `publish-database`, and `publish-frontend`. The two test jobs
run in parallel through reusable workflows. The backend runs Maven tests and
SonarCloud; the frontend builds Apache, checks its configuration with `httpd -t`,
and verifies HTTP forwarding to a mock backend. There is no separate frontend UI
application in this repository.

Each publishing job calls `publish-docker.yml` with its own context and image
name. All three require both test jobs to pass and run only on pushes to `main`.
They run independently in parallel and build the same commit that passed CI.
Publication is not atomic: if one image job fails, another may already have
published its image. Use matching SHA tags when selecting a release.

Each publishing job uses Docker Buildx and logs in with `docker/login-action`.
Each `docker/build-push-action` step has its own build context:

| Context | Docker Hub image |
| --- | --- |
| `simple-api` | `<username>/tp-devops-simple-api` |
| `database` | `<username>/tp-devops-database` |
| `http-server` | `<username>/tp-devops-httpd` |

Each image receives `latest` and a full Git commit SHA tag. The SHA tag lets you
select a specific tested revision for deployment or rollback. Building and
pushing an image does not deploy or restart an application.

### Manual rollback

In GitHub, open **Actions → Rollback Docker images → Run workflow**, select
`main`, and enter the full 40-character lowercase commit SHA of a previously
published working release. The workflow uses the existing Docker Hub secrets.
It checks that all three SHA-tagged images exist, then restores their `latest`
tags to those images without rebuilding. The selected SHA tags stay available.
Normal main publication and rollback share a concurrency lock to prevent them
from updating tags at the same time.

To demonstrate the bonus, publish release A and then release B, run rollback
with A's SHA, and verify that each image's `latest` digest matches its A tag in
Docker Hub. The workflow summary records the restored version for each image.
A nonexistent SHA fails the checks before any tags are changed.

This is a manual registry rollback. It does not deploy containers, restart an
application, or restore database data. A deployed application must pull the
restored images and recreate its containers separately. Tag updates across
three repositories are not atomic; if an update fails partway through, rerun
the rollback. A later successful main pipeline will publish a new `latest`.

### Configure accounts before enabling delivery

Credentials are not configured by this commit. In GitHub, open **Settings →
Secrets and variables → Actions** and add these **repository secrets**:

| Secret | Value |
| --- | --- |
| `DOCKERHUB_USERNAME` | Your lowercase Docker Hub username |
| `DOCKERHUB_TOKEN` | A Docker Hub access token with permission to push to the three repositories |
| `SONAR_TOKEN` | A SonarCloud token with permission to analyze this project |

Under the **Variables** tab, add these non-secret repository variables:

| Variable | Value |
| --- | --- |
| `SONAR_PROJECT_KEY` | The project key shown in SonarCloud |
| `SONAR_ORGANIZATION` | The organization key shown in SonarCloud |

Use repository-level settings: these reusable workflows do not select a GitHub
Environment. Do not commit tokens or put them in Dockerfiles, build arguments,
or ordinary repository variables. Create the three Docker Hub repositories under
the configured username, with the visibility you want.

### SonarCloud quality gate

1. Sign in to SonarCloud, create or select your organization, and import this
   GitHub repository. Set its main branch to `main` and record both keys.
2. Select CI-based analysis with GitHub Actions and disable Automatic Analysis
   for this project to avoid conflicting analysis methods.
3. Select the built-in **Sonar way** quality gate in the project settings and
   configure the new-code definition for the project. Conditions are managed
   in SonarCloud; this repository enforces the resulting gate status.
4. Add the token and variables listed above. For this public project, check
   eligibility for the free **OSS plan**, which supports branch and pull-request
   analysis. The standard Free plan limits branch analysis to the main branch;
   it cannot run this pipeline's `develop` analysis.
5. When you choose to push these changes, check the Actions run and the linked
   SonarCloud dashboard. A failed gate or a five-minute gate timeout must fail
   `test-backend` and skip Docker publication.

The scanner runs after `mvn -B clean verify`, reusing compiled classes and the
JaCoCo XML report at `simple-api/target/site/jacoco/jacoco.xml`. JaCoCo's report
goal runs in `verify` so it includes integration-test coverage too. The scanner
version is pinned, authentication comes from `SONAR_TOKEN`, and
`sonar.qualitygate.wait=true` turns the analysis result into a CI requirement.
Missing credentials or keys fail trusted runs with a setup error. Fork and
Dependabot PRs run tests without analysis; every push to `main` still requires
the gate before publication.

To stop failing code from being merged, configure a GitHub branch ruleset for
`main` requiring pull requests and the backend workflow's check. The workflow
alone blocks delivery, but cannot prevent a direct push to the branch.

### Exercise answers

**2-2  Why use secured variables?** GitHub Actions secrets keep credentials out
of source code and Git history, encrypt them at rest, and make them available
to the authorized workflow steps. They also mask known secret values in logs.
Tokens can be rotated without changing the code; avoid printing them even with
masking enabled.

**2-3  Why `needs: [test-backend, test-frontend]`?** It orders publication after successful backend and frontend tests
and quality analysis. Without it, jobs can run in parallel and publish images
from code whose tests or gate later fail. The exercise's `build-and-test-backend`
is named `test-backend` here; `needs` must match the actual job ID.

**2-4  Why push Docker images?** A registry stores and distributes the built
images so servers and teammates can pull the same application artifact without
rebuilding the source. Version tags make deployments traceable and allow a
previous image to be selected for rollback.



