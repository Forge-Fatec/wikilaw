from fastapi.testclient import TestClient
from app import create_app, DIMENSIONS, FINGERPRINT


class StubEncoder:
    def encode(self, texts, kind):
        return [[1.0] + [0.0] * (DIMENSIONS - 1) for _ in texts]


def test_contract_and_query_passage_modes():
    with TestClient(create_app(StubEncoder)) as client:
        assert client.get('/health').json()['model'] == FINGERPRINT
        for kind in ['query', 'passage']:
            response = client.post('/embed', json={'texts': ['Direito do consumidor'], 'kind': kind})
            assert response.status_code == 200
            assert len(response.json()['vectors'][0]) == DIMENSIONS


def test_limits_and_invalid_input_are_rejected():
    with TestClient(create_app(StubEncoder)) as client:
        for texts in [[], [' '], ['x' * 100001], ['x'] * 17, ['x' * 100000] * 3]:
            assert client.post('/embed', json={'texts': texts, 'kind': 'query'}).status_code == 422
        assert client.post('/embed', json={'texts': ['texto'], 'kind': 'invalid'}).status_code == 422


def test_private_content_is_not_exposed_on_inference_failure():
    class Broken:
        def encode(self, texts, kind):
            raise RuntimeError('PRIVATE_DOCUMENT_TEXT')
    with TestClient(create_app(Broken)) as client:
        response = client.post('/embed', json={'texts': ['PRIVATE_DOCUMENT_TEXT'], 'kind': 'query'})
        assert response.status_code == 503
        assert 'PRIVATE_DOCUMENT_TEXT' not in response.text


def test_invalid_vectors_are_rejected():
    class Broken:
        def encode(self, texts, kind):
            return [[float('nan')] * DIMENSIONS]
    with TestClient(create_app(Broken)) as client:
        assert client.post('/embed', json={'texts': ['texto'], 'kind': 'query'}).status_code == 503
