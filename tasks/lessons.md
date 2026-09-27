# Lessons

- Delegated review liveness: if a Reviewer produces no verdict after bounded waits, stop that attempt, relaunch a narrower read-only review, and keep Jovi informed. A review gate stays pending until an actual verdict arrives; silence is never PASS.
