# Security and Private Data

Report security concerns privately to the repository owner. Do not post candidate
records, passwords, session tokens, enrollment material or private documents in
issues, pull requests or screenshots.

This project processes personal information and examination material. Production
operators are responsible for access control, HTTPS, backups, retention policies,
monitoring and deployment-specific security review.

Never commit `uploads`, databases, logs, browser profiles, private keys or local
configuration containing credentials. Review staged changes before every push.
If a real credential is accidentally published, revoke it immediately; deleting
the file from the latest commit does not remove it from Git history.

Development enrollment defaults and local HTTP URLs are not production settings.
Desktop security features do not eliminate the need for exam supervision.
