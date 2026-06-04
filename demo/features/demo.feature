Feature: API endpoint testing
  As a developer
  I want to test API responses against expected data
  So that I can verify the API behaves correctly

  Scenario: Successful user login
    When I send POST request to "/api/login" with body "fixtures/login.json"
    Then the response status code should be 200
    And the response body should match "fixtures/responses/success.json"

  Scenario: Invalid credentials
    When I send POST request to "/api/login" with body
    """
    {"username": "admin", "password": "wrong"}
    """
    Then the response status code should be 401
    And the response body should match "fixtures/responses/error.xml"

  Scenario: Multiple files
    When I compare responses against:
      | file                              |
      | fixtures/responses/success.json   |
      | fixtures/responses/error.xml      |
    Then all files match expected values
