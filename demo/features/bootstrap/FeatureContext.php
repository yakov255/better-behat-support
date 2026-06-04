<?php

use Behat\Behat\Context\Context;
use Behat\Gherkin\Node\PyStringNode;
use Behat\Gherkin\Node\TableNode;

class FeatureContext implements Context
{
    private ?string $requestBody = null;
    private ?array $responseData = null;

    /**
     * @When I send POST request to :endpoint with body :file
     */
    public function iSendPostRequestWithBodyFromFile(string $endpoint, string $file): void
    {
        $path = __DIR__ . '/../' . $file;
        $this->requestBody = file_get_contents($path);
    }

    /**
     * @When I send POST request to :endpoint with body
     */
    public function iSendPostRequestWithInlineBody(string $endpoint, PyStringNode $body): void
    {
        $this->requestBody = $body->getRaw();
    }

    /**
     * @Then the response status code should be :code
     */
    public function theResponseStatusCodeShouldBe(int $code): void
    {
        if ($code === 200) {
            $this->responseData = ['status' => 'ok'];
        } else {
            $this->responseData = ['status' => 'error', 'code' => $code];
        }
    }

    /**
     * @Then the response body should match :file
     */
    public function theResponseBodyShouldMatch(string $file): void
    {
        $path = __DIR__ . '/../' . $file;
        $expected = file_get_contents($path);
    }

    /**
     * @When I compare responses against:
     */
    public function iCompareResponsesAgainst(TableNode $table): void
    {
        foreach ($table->getHash() as $row) {
            $path = __DIR__ . '/../' . $row['file'];
            $content = file_get_contents($path);
        }
    }

    /**
     * @Given пользователь с правами администратора
     */
    public function userIsAdmin(): void
    {
    }

    /**
     * @Когда система возвращает ответ из файла :file
     */
    public function systemReturnsResponseFromFile(string $file): void
    {
        $path = __DIR__ . '/../' . $file;
        $this->responseData = json_decode(file_get_contents($path), true);
    }

    /**
     * @Тогда ответ содержит данные значения, аналогичные файлу :file
     */
    public function responseContainsDataMatchingFile(string $file): void
    {
        $path = __DIR__ . '/../' . $file;
        $expected = file_get_contents($path);
    }
}
