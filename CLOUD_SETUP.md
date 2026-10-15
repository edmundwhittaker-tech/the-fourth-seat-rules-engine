# Cloud-only setup: GitHub + Codespaces + GitHub Pages

**You do not need to install Node.js, Git, or an IDE on your computer.** Everything runs in a browser or on GitHub infrastructure. GitHub Pages hosts an **experimental single-browser playground**; it does not operate a trusted multiplayer rules server.

## If the repository is already on GitHub

1. Open the repository on github.com.
2. Select **Code → Codespaces → Create codespace on main**. GitHub will launch a browser-based editor and cloud computer.
3. Wait for the container to complete its post-create step (`npm ci`).
4. In the browser-based terminal run `npm test` to check the rules engine.
5. Run `npm run dev`. The **Ports** panel offers a link to the running playground at port 8080. Nothing runs on your local machine.
6. Edit files, then use the **Source Control** panel to commit and push changes. GitHub Actions tests and publishes the playground automatically on changes to `main`.

## Enable or inspect the public playground

Open [repository Pages settings](https://github.com/edmundwhittaker-tech/the-fourth-seat-rules-engine/settings/pages)
and choose **GitHub Actions** as the source. The workflow is already included.
In **Actions → Test and deploy rules playground**, use **Run workflow** or re-run
a failed deployment after enabling Pages.

Pushes to main and pull requests compile TypeScript and run the tests with Node
22 and `npm ci`. Only successful main-branch runs publish the playground. Pull
requests cannot deploy. A deployment failure does not hide the test job result.

[Open the playground](https://edmundwhittaker-tech.github.io/the-fourth-seat-rules-engine/)
after deployment succeeds. Use **Pass to next resolution / step** twice to reach
main1, play Cliffside, tap it, cast Spark Lance targeting Blake, then pass a
round to resolve it. Blake should have 37 life. **Restart demo** resets the game.

For small source edits you can also use GitHub's file editor. Commit the edit
(or open a pull request), then inspect Actions. No local software is required.
Codespaces provides a cloud terminal when you want to run tests before pushing.

## What runs where?

| Component | Location | Purpose |
|---|---|---|
| GitHub repository | GitHub cloud | Tracks source, issues, changes, reviews |
| GitHub Codespaces | GitHub cloud | Browser editor, terminal, Node, tests |
| GitHub Actions | GitHub cloud | Compiles TypeScript, runs tests, publishes demo |
| GitHub Pages | GitHub cloud | Hosts static demo UI and client-side experimental engine |
| Authoritative multiplayer backend | **Not built yet** | Eventually validates actions securely for The Fourth Seat |

## Important security boundary

A GitHub Pages page is not a secure multiplayer server. Browsers are untrusted. Do **not** send all opponents' hidden cards to real game clients, or let a browser submit a trusted authoritative state snapshot. For multiplayer integration, the game host/backend must own the full game state and validate every proposed action; clients only receive filtered views.
