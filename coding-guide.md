# Coding Guide

Applied to all projects. Appended to project CLAUDE.md via `/init`.

---

## Philosophy

### 1. Tidy First (Kent Beck)

Tidy before feature, never mix them.

```
1. Tidy  — make code readable
2. Feature — add functionality
3. Tidy  — clean up new code
```

Rules:
- Never mix tidying and feature work in one commit
- Keep tidying small (5-15 min)
- If code is hard to understand, tidy first
- Boy Scout Rule: leave files cleaner than you found them

### 2. Make it Work → Right → Fast

Follow this order strictly. Never skip a step.

- **Work**: get it running, tests passing. Ugly is OK.
- **Right**: refactor, apply Tidy First, improve structure.
- **Fast**: measure first, optimize bottlenecks only.

---

## Priority

1. Correctness
2. Readability
3. Maintainability
4. Performance (only when needed)
5. Extensibility (don't over-engineer)

---

## Code Style

### Functions
- Max 20 lines
- Single responsibility
- Guard clauses over nested ifs (max 3 levels)
- Descriptive names (verb + noun)
- Self-documenting over comments

```typescript
// Good
function calculateTotalPrice(items: Item[]): number {
  return items.reduce((sum, item) => sum + item.price, 0);
}

// Bad
function processUserData(data: any) { /* 100 lines... */ }
```

### Guard Clauses

```typescript
// Good
function createUser(data: UserInput) {
  if (!data.email) throw new Error('Email required');
  if (!isValidEmail(data.email)) throw new Error('Invalid email');
  if (data.age < 18) throw new Error('Must be 18+');
  return saveUser(data);
}

// Bad — nested ifs
function createUser(data: UserInput) {
  if (data.email) {
    if (isValidEmail(data.email)) {
      if (data.age >= 18) { return saveUser(data); }
    }
  }
}
```

### Naming

```typescript
// Good
const totalAmount = calculateTotal(transactions);
const isUserActive = checkUserStatus(userId);

// Bad
const x = calc(data);
const flag = check(id);
```

---

## Pre-coding Checklist

- [ ] Can a newcomer understand this in 5 min?
- [ ] Functions under 20 lines?
- [ ] Nesting under 3 levels?
- [ ] Clear variable/function names?
- [ ] Tests exist?
- [ ] No dead code or commented-out code?

---

## Refactoring Timing

- **Immediate (< 5 min)**: typos, naming, dead code, formatting
- **Before feature (10-30 min)**: extract functions, remove duplication, guard clauses
- **Later (1h+)**: architecture changes, data redesign → file as separate issue

---

## Tidy Patterns (Kent Beck)

1. Guard Clauses — early returns to reduce nesting
2. Extract Helper Function — split long functions
3. Explaining Variable — name complex expressions
4. Dead Code Removal — delete unused code
5. Normalize Symmetries — keep symmetric structure
6. Explaining Comment → Function Name — replace comments with named functions

---

## Testing

Priority: behavior → edge cases → error cases

When to write:
- Before feature (TDD)
- Immediately after feature
- Before fixing a bug (reproduction test first)

---

## Performance

Measure → Optimize → Measure again. Never guess.
- Only the slowest part, one at a time.

---

## Prohibited

- Committing broken code
- Features without tests
- Meaningless commit messages
- Large single commits (100+ lines)
- Mixing tidy and feature in one commit
- Over-abstraction (YAGNI)
- Premature optimization

---

## Self-review Before Commit

- [ ] Code is easy to understand?
- [ ] Tests pass?
- [ ] Commit has single purpose?
- [ ] Commit message is clear and in Korean?
- [ ] No unnecessary changes?
- [ ] No dead code left?

---

## Exceptions

Apply principles flexibly for:
1. **Prototype/POC** — Work only
2. **Hotfix** — fix first, tidy in follow-up
3. **Legacy code** — incremental improvement
4. **Experimental** — validate then decide

Always follow up with a tidy commit.
