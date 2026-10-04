# RideLink Git branch workflow

```mermaid
gitGraph LR:
    commit id: "Stable baseline"
    branch dev
    checkout dev

    branch feature/eranjana/driver-jwt-security
    checkout feature/eranjana/driver-jwt-security
    commit id: "Driver JWT security"
    checkout dev
    merge feature/eranjana/driver-jwt-security id: "Driver security PR"

    branch feature/eranjana/ride-jwt-security
    checkout feature/eranjana/ride-jwt-security
    commit id: "Ride JWT security"
    checkout dev
    merge feature/eranjana/ride-jwt-security id: "Ride security PR"

    branch feature/dineth/fare-payment-jwt-security
    checkout feature/dineth/fare-payment-jwt-security
    commit id: "Fare payment security"
    checkout dev
    merge feature/dineth/fare-payment-jwt-security id: "Fare security PR"

    branch chore/eranjana/github-actions-ci
    checkout chore/eranjana/github-actions-ci
    commit id: "CI workflow"
    checkout dev
    merge chore/eranjana/github-actions-ci id: "CI PR"

    branch chore/eranjana/final-postman-docs
    checkout chore/eranjana/final-postman-docs
    commit id: "Final docs"
    checkout dev
    merge chore/eranjana/final-postman-docs id: "Documentation PR"

    branch release
    checkout release
    commit id: "Release validation"
    checkout main
    merge release id: "Release PR"
    commit id: "Version 1.0.0" tag: "v1.0.0"
```

All `feature/...`, `chore/...`, and `fix/...` branches merge into `dev` through pull requests. `dev` progresses to `release`, then `main`; the validated `main` state receives the `v1.0.0` tag. Eranjana's changes are reviewed by Dineth, and Dineth's changes are reviewed by Eranjana.
