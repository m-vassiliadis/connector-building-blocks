import pytest

from edcpy.edc_api import CatalogContent


def test_catalogue_selection_prefers_exact_canonical_id():
    catalogue = CatalogContent(
        {
            "dcat:dataset": [
                {"@id": "GET-status", "name": "GET /status"},
                {"@id": "payments--GET-status", "name": "payments: GET /status"},
            ]
        }
    )

    selected = catalogue.find_one_dataset("payments--GET-status")

    assert selected["@id"] == "payments--GET-status"


def test_catalogue_selection_rejects_ambiguous_partial_query():
    catalogue = CatalogContent(
        {
            "dcat:dataset": [
                {"@id": "GET-status", "name": "GET /status"},
                {"@id": "payments--GET-status", "name": "payments: GET /status"},
            ]
        }
    )

    with pytest.raises(ValueError, match="ambiguous"):
        catalogue.find_one_dataset("status")
