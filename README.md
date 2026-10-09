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

| Event | Backend and frontend tests | Docker Hub publication |
| --- | --- | --- |
| Push to `main` | Yes | Only after tests pass |
| Push to `develop` | Yes | No |
| PR to `main` or `develop` | Yes | No |

The entry point exposes six jobs: `test-backend`, `test-frontend`,
`publish-backend`, `publish-database`, `publish-httpd`, and `publish-front`. The
two test jobs run in parallel through reusable workflows. The backend runs Maven
tests; the frontend job builds the Vue front image, checks the Apache
configuration with `httpd -t`, and verifies that Apache sends `/api/` to a mock
backend and `/` to the front.

Each publishing job calls `publish-docker.yml` with its own context and image
name. All of them require both test jobs to pass and run only on pushes to `main`.
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
| `front` | `<username>/tp-devops-front` |

Each image receives `latest` and a full Git commit SHA tag. The SHA tag lets you
select a specific tested revision for deployment or rollback. Building and
pushing an image does not deploy or restart an application.

### Manual rollback

In GitHub, open **Actions → Rollback Docker images → Run workflow**, select
`main`, and enter the full 40-character lowercase commit SHA of a previously
published working release. The workflow uses the existing Docker Hub secrets.
It checks that all SHA-tagged images exist, then restores their `latest`
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
repositories are not atomic; if an update fails partway through, rerun
the rollback. A later successful main pipeline will publish a new `latest`.

### Configure accounts before enabling delivery

Credentials are not configured by this commit. In GitHub, open **Settings →
Secrets and variables → Actions** and add these **repository secrets**:

| Secret | Value |
| --- | --- |
| `DOCKERHUB_USERNAME` | Your lowercase Docker Hub username |
| `DOCKERHUB_TOKEN` | A Docker Hub access token with permission to push to the image repositories |

Use repository-level settings: these reusable workflows do not select a GitHub
Environment. Do not commit tokens or put them in Dockerfiles, build arguments,
or ordinary repository variables. Create the Docker Hub repositories under
the configured username, with the visibility you want.

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



