# Demo checklist
- [ ] Laptop charged, internet available (Bootstrap loads from a CDN), Supabase project awake
- [ ] App started 10 minutes early; `http://localhost:8080` (or demo URL) loads
- [ ] Browser windows ready: 1 normal + 1 private window (two roles at once)
- [ ] Demo accounts known (README table), photo (small JPEG) ready for upload

Script (about 8 minutes)
1. Landing page, register a new citizen (show validation of weak password)
2. Citizen: report an issue with photo; see it in dashboard and the timeline
3. Authority (private window): filter list, open complaint, start review, set priority High, assign to worker
4. Worker: open assignment, start work, add note, mark completed
5. Authority: verify and close. Try an invalid action (e.g. close a new complaint) to show business rules
6. Citizen: view the timeline, give feedback
7. Show security: citizen opens `/authority/dashboard` -> 403 page; log out
8. Admin: overview counts, create a user, manage categories
9. Show code: `ComplaintStatus.allowedNext()`, `Complaint.changeStatus()`, `NotificationService` implementations
10. Run `mvn test` (or show a recent green run)

Backup plan: screenshots/video of the full flow, and a local run if hosting is down.
