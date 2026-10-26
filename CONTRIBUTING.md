# Contributing

## Branches

- `main` is the stable, integrated project. Nobody pushes to it directly; changes go in through Pull Requests.
- Each member works on their own branch, created from `main`:
  - `feature/week1-member1`
  - `feature/week1-member2`
  - `feature/week1-member3`
- In later weeks, use `feature/weekN-memberX` or a short topic name such as `feature/alerts-email`.

## Ownership

Each module has one owner (see [WEEK1_TASK_DISTRIBUTION.md](WEEK1_TASK_DISTRIBUTION.md)). Change files in your own module. If you need a change in someone else's module, ask the owner or open a small PR and ask them to review it.

Files that everyone depends on (`backend/pom.xml`, `backend/src/main/resources/application*.yml`, `backend/src/main/resources/db/migration/`, `frontend/package.json`, `docker-compose.yml`) are changed only in a PR of their own, so conflicts stay small.

## Commits

- Commit with your own Git identity (your own name and the email of your GitHub account). Commit only work you did yourself.
- Never rewrite another person's commits or change commit authors.
- Write short messages in the imperative: `Add curl examples for key API`, `Fix provider form validation`.
- Keep commits small and about one thing.

## Never commit

- `.env` or any file with real API keys, passwords or the encryption key
- Build output and dependencies: `node_modules/`, `backend/target/`, `frontend/dist/`
- Local databases and logs: `backend/data/`, `*.log`

Before every commit, check with `git status` and `git diff --staged`.

## Pull Requests

1. Push your branch and open a PR to `main`.
2. In the description, say what you changed, how you checked it, and anything still open.
3. At least one other member reviews it. Reviewers run the change locally when they can and leave comments.
4. The author answers the comments and pushes fixes to the same branch.
5. After approval, the author merges it with **Squash and merge** or **Merge**. Delete the branch afterwards.

## Before opening a PR

```bash
cd backend && ./mvnw package     # backend builds
cd frontend && npm run build     # dashboard builds
cd frontend && npm run lint      # no lint errors
```

Then run the app and check your change by hand (see "Try it end to end" in the README). Automated tests are planned for a later week.

## Code style

- Backend: follow the style of the existing code (tabs, Spring conventions, short Javadoc on public classes).
- Frontend: TypeScript, function components, Tailwind classes; `npm run lint` must pass.
- Money is `BigDecimal` / `NUMERIC`, never floating point.
- Never add a migration change to `V1__init.sql`. Add a new file such as `V2__add_x.sql`.
