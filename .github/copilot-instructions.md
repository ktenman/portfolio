# GitHub Copilot Instructions

## Commit Message Guidelines

When suggesting commit messages, follow these conventions:

### Format

- Use sentence case (capitalize first letter only)
- Start with a verb in imperative mood
- No "chore:", "feat:", "fix:" or other prefixes
- Keep messages concise and descriptive

### Examples of Good Commit Messages

- Add Trivy configuration and scanning workflow for automated container vulnerability detection
- Update README and Trivy workflow for improved clarity and dynamic job naming
- Enhance container scan report formatting and update links for improved clarity
- Fix PostgreSQL connection timeout in integration tests
- Refactor portfolio calculation logic for better performance

### Examples to Avoid

- chore: update dependencies
- feat: add new feature
- fix: bug fix
- Updated files
- Minor changes

### Special Cases

- Automated commits from GitHub Actions should include `[skip ci]` suffix
- Example: `Update container security scan report [skip ci]`

### Focus Areas

- Describe WHAT changed and WHY (when not obvious)
- Be specific about the scope of changes
- Mention key components or areas affected
