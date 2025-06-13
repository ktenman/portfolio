# Test Review Checklist

Use this checklist when reviewing test code in pull requests to ensure high-quality, effective tests.

## ✅ Test Coverage & Structure

### Basic Coverage

- [ ] **Happy Path**: Does the test cover the main success scenario?
- [ ] **Error Conditions**: Are failure cases and exceptions tested?
- [ ] **Edge Cases**: Are boundary conditions covered (null, empty, zero, max values)?
- [ ] **Business Logic**: Are business rules and calculations thoroughly tested?

### Test Organization

- [ ] **Descriptive Names**: Test method names clearly describe what is being tested
- [ ] **Arrange-Act-Assert**: Tests follow a clear structure with setup, execution, and verification
- [ ] **Single Responsibility**: Each test focuses on one specific behavior
- [ ] **No Test Logic**: Tests are straightforward without complex conditionals or loops

## ✅ Assertions & Verification

### Quality Assertions

- [ ] **Behavior Testing**: Tests verify behavior, not implementation details
- [ ] **Specific Assertions**: Uses precise assertions (not just `assertThat(result).isNotNull()`)
- [ ] **Multiple Aspects**: Verifies all relevant aspects of the result
- [ ] **Error Messages**: Custom error messages explain what went wrong

### Financial Calculations (Portfolio-Specific)

- [ ] **Precision**: Financial calculations use appropriate precision (avoid `assertEquals` for doubles)
- [ ] **Range Validation**: XIRR results are within reasonable bounds (-100% to +10000%)
- [ ] **Rounding**: Monetary values are properly rounded to appropriate decimal places

## ✅ Test Data & Isolation

### Test Data

- [ ] **Realistic Data**: Test data represents real-world scenarios
- [ ] **Minimal Data**: Uses the minimum data necessary to test the behavior
- [ ] **Clear Intent**: Test data makes the test's purpose obvious
- [ ] **Date Handling**: Financial dates are realistic and properly ordered

### Isolation & Independence

- [ ] **Independent Tests**: Tests don't depend on execution order
- [ ] **Clean State**: Each test starts with a clean state
- [ ] **No Shared Mutable State**: Tests don't share mutable data
- [ ] **Mocking Strategy**: External dependencies are properly mocked

## ✅ Integration & Performance

### Integration Tests

- [ ] **Real Dependencies**: Integration tests use real external services (databases, etc.)
- [ ] **Transaction Boundaries**: Database transactions are properly handled
- [ ] **Testcontainers**: Uses Testcontainers for database/Redis dependencies
- [ ] **@IntegrationTest**: Properly annotated for CI pipeline classification

### Performance Considerations

- [ ] **Fast Unit Tests**: Unit tests run quickly (< 100ms each)
- [ ] **Reasonable Integration Time**: Integration tests have acceptable runtime
- [ ] **No Network Calls**: Unit tests don't make real network calls
- [ ] **Resource Cleanup**: Tests clean up resources properly

## ✅ Mutation Testing Mindset

### Code Change Resilience

- [ ] **Mutation Resistance**: Would the test fail if production code logic changed?
- [ ] **Boundary Testing**: Tests values at boundaries (0, 1, -1, max, min)
- [ ] **Operator Coverage**: Tests different comparison operators and logic
- [ ] **Branch Coverage**: All conditional branches are exercised

### Property-Based Thinking

- [ ] **Invariants**: Considers properties that should always hold true
- [ ] **Edge Case Generation**: Uses property-based tests for complex calculations
- [ ] **Input Validation**: Tests behavior with various input combinations

## ✅ Spring Boot Specific

### Framework Integration

- [ ] **Annotation Testing**: Proper use of `@MockBean`, `@SpyBean`, etc.
- [ ] **Context Loading**: Minimal context loading for unit tests
- [ ] **Profile Handling**: Correct test profiles are used
- [ ] **Auto-Configuration**: Tests don't unnecessarily load full application context

### Service Layer

- [ ] **Transactional Behavior**: Tests verify transactional semantics where applicable
- [ ] **Exception Handling**: Service exceptions are properly tested
- [ ] **Async Behavior**: Coroutine-based services are tested correctly

## ✅ Code Quality

### Maintainability

- [ ] **Readable Code**: Test code is clean and easy to understand
- [ ] **DRY Principle**: Common test setup is extracted to helper methods
- [ ] **Consistent Style**: Follows project testing conventions
- [ ] **Documentation**: Complex test scenarios are documented

### Review Process

- [ ] **Test First**: New features include tests before implementation changes
- [ ] **Refactoring Safety**: Existing tests pass after refactoring
- [ ] **Coverage Maintained**: Code coverage doesn't decrease significantly
- [ ] **CI Pipeline**: All tests pass in the CI environment

## 🚨 Red Flags

Watch out for these anti-patterns:

- ❌ Tests that always pass regardless of implementation
- ❌ Tests with unclear or generic names like `testMethod1()`
- ❌ Tests that test multiple unrelated behaviors
- ❌ Hardcoded test data without explanation
- ❌ Tests that rely on specific timing or thread ordering
- ❌ Integration tests that don't actually test integration
- ❌ Mocking everything in a unit test (testing mocks, not code)
- ❌ Tests that duplicate production logic instead of testing it

## 💡 Tips for Reviewers

1. **Ask "What if?"**: What if this calculation was off by one? Would the test catch it?
2. **Consider Edge Cases**: What happens with negative numbers, null values, empty collections?
3. **Think Like an Attacker**: How could this code fail in production?
4. **Verify Intent**: Does the test actually verify the business requirement?
5. **Check Assumptions**: Are the test assumptions documented and valid?

Remember: The goal is not just code coverage, but **confidence** that the code works correctly in all scenarios.
