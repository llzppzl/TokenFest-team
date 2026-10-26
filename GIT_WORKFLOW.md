# Git Workflow

We use a feature-branch workflow. `main` is the integrated project; each member works on a branch and merges through a Pull Request.

The shared repository is **https://github.com/llzppzl/TokenFest-team** (public).

## 0. One-time setup (every member)

Set your own Git identity. Use the email of your GitHub account, or its noreply address from GitHub → Settings → Emails.

```bash
git config --global user.name "Your Name"
git config --global user.email "you@example.com"
```

Clone the repository:

```bash
git clone https://github.com/llzppzl/TokenFest-team.git
cd TokenFest-team
```

Check that the project builds (see README for the requirements):

```bash
(cd backend && ./mvnw package)
(cd frontend && npm install && npm run build)
```

## Week 1 only: adding your package

The Week 1 code comes as three packages (`Phoebe_L.zip`, `Xiaohan_Wang.zip`, `Mingzhu_Wan.zip`), one per module. Each has a `START_HERE.md` and a `code/` folder. Merge them in the order 1 → 2 → 3.

**Member 1 (Phoebe L)** pushes first (the repository is already created and empty):

```bash
cd Phoebe_L/code
git init -b main
git add -A
git commit -m "Add gateway core and project setup" -m "Code originally written by Phoebe L."
git remote add origin https://github.com/llzppzl/TokenFest-team.git
git push -u origin main
```

Then invite Members 2 and 3 as collaborators (Settings → Collaborators).

**Member 2 (Xiaohan Wang)** (after accepting the invitation):

```bash
git clone https://github.com/llzppzl/TokenFest-team.git
cd TokenFest-team
git switch -c feature/week1-member2
cp -R /path/to/Xiaohan_Wang/code/. .
git add -A
git status                       # only your module's files
git commit -m "Add keys, auth and accounting module" -m "Code originally written by Phoebe L."
git push -u origin feature/week1-member2
```

Open a PR to `main`. After it is merged, the backend builds (`cd backend && ./mvnw package`).

**Member 3 (Mingzhu Wan):** the same steps, after Member 2's PR is merged, with branch `feature/week1-member3`, package `Mingzhu_Wan/code/` and message `"Add dashboard and Docker deployment"`. After the merge, the whole project runs.

The rest of Week 1 (the work in `WEEK1_TASK_DISTRIBUTION.md`) then uses the steps below.

## 1. Create your branch from the latest `main`

| Member | Branch |
|---|---|
| Member 1: Phoebe L | `feature/week1-member1` |
| Member 2: Xiaohan Wang | `feature/week1-member2` |
| Member 3: Mingzhu Wan | `feature/week1-member3` |

```bash
git switch main
git pull origin main
git switch -c feature/week1-member1     # use your own branch name
```

## 2. Work and commit

Change only the files of your module (see `WEEK1_TASK_DISTRIBUTION.md`). Then:

```bash
git status                       # check what changed; no .env, node_modules, target, dist
git add <the files you changed>
git diff --staged                # read what you are about to commit
git commit -m "Describe your change"
```

Commit as often as you like. Each commit is your own work under your own name.

## 3. Push your branch

First push:

```bash
git push -u origin feature/week1-member1
```

Later pushes on the same branch:

```bash
git push
```

## 4. Open a Pull Request

On GitHub: **Pull requests → New pull request**, base `main`, compare `feature/week1-memberX`. Fill in:

- **What:** what you changed
- **How I checked it:** commands you ran, what you clicked in the dashboard
- **Open points:** anything not finished

Add the other two members as reviewers.

## 5. Review

Reviewers check the change out locally:

```bash
git fetch origin
git switch feature/week1-member2         # the branch under review
(cd backend && ./mvnw package) && (cd frontend && npm run build)
```

Then comment on GitHub, and **Approve** or **Request changes**. The author fixes comments on the same branch:

```bash
git switch feature/week1-member2
# edit files
git add <files>
git commit -m "Address review comments"
git push
```

## 6. Keep your branch up to date

If `main` changed while you worked (for example another PR was merged):

```bash
git switch main
git pull origin main
git switch feature/week1-member1
git merge main
# fix any conflicts, then:
git add <fixed files>
git commit
git push
```

## 7. Merge

After at least one approval, the author clicks **Merge pull request** on GitHub and deletes the branch. Then everyone updates:

```bash
git switch main
git pull origin main
```

## Rules

- Never push directly to `main`, and never `git push --force` on `main`.
- Never change the author of someone else's commit, and never commit work as someone else.
- Never commit `.env` or real API keys. If it happens, tell the team right away: the key must be revoked, deleting the file is not enough.
