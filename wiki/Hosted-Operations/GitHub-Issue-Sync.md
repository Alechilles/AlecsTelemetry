---
title: "GitHub Issue Sync"
order: 5
published: true
draft: false
---

# GitHub Issue Sync

Parent: [Hosted Operations](/mod/alecs-telemetry/hosted-operations) | [Home](/mod/alecs-telemetry/home)

GitHub issue sync lets project members create linked GitHub issues from portal issues and optionally mirror recurring issue activity.

## Setup

1. Sign in to the portal with GitHub or link GitHub from Account.
2. Open the project.
3. Open project admin.
4. Find GitHub issue integration.
5. Install or connect the GitHub App when prompted.
6. Choose a repository from the available repositories.
7. Set whether auto-create is enabled.
8. Set the auto-create threshold.
9. Save GitHub integration.

> [Screenshot Placeholder: Project admin GitHub issue integration panel]

## Manual Issue Creation

After setup, open a portal issue and choose **Create GitHub issue**. The portal links the created GitHub issue back to the portal issue workspace.

## Auto-Create

Auto-create can create GitHub issues after an issue reaches the configured occurrence threshold. Use a threshold high enough to avoid sending one-off noise to GitHub.

## Verify

1. Configure the GitHub integration.
2. Open a portal issue.
3. Create a GitHub issue manually.
4. Confirm the portal issue shows the linked GitHub issue number.
5. Open GitHub and confirm the issue exists in the selected repository.

## Troubleshooting

- Repository missing: reinstall the GitHub App and grant access to the repository.
- Create button disabled: you need maintainer access or higher.
- Auto-create threshold rejected: use a whole number greater than 0.
- GitHub issue not updated: check project admin integration state.
