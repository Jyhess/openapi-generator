# coding: utf-8

import json
from aiohttp import web

from openapi_server.models.api_response import ApiResponse
from openapi_server.models.pet import Pet


async def test_add_pet(client):
    """Test case for add_pet

    Add a new pet to the store
    """
    body = Pet().to_dict()
    headers = {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
        'Authorization': 'Bearer special-key',
    }
    response = await client.request(
        method='POST',
        path='/v2/pet',
        headers=headers,
        json=body,
        )
    assert response.status == 200, 'Response body is : ' + (await response.read()).decode('utf-8')


async def test_delete_pet(client):
    """Test case for delete_pet

    Deletes a pet
    """
    headers = {
        'Accept': 'application/json',
        'api_key': 'api_key_example',
        'Authorization': 'Bearer special-key',
    }
    response = await client.request(
        method='DELETE',
        path='/v2/pet/{pet_id}'.format(pet_id=56),
        headers=headers,
        )
    assert response.status == 200, 'Response body is : ' + (await response.read()).decode('utf-8')


async def test_find_pets_by_status(client):
    """Test case for find_pets_by_status

    Finds Pets by status
    """
    params = [('status', 'available')]
    headers = {
        'Accept': 'application/json',
        'Authorization': 'Bearer special-key',
    }
    response = await client.request(
        method='GET',
        path='/v2/pet/findByStatus',
        headers=headers,
        params=params,
        )
    assert response.status == 200, 'Response body is : ' + (await response.read()).decode('utf-8')


async def test_find_pets_by_tags(client):
    """Test case for find_pets_by_tags

    Finds Pets by tags
    """
    params = [('tags', 'tags_example')]
    headers = {
        'Accept': 'application/json',
        'Authorization': 'Bearer special-key',
    }
    response = await client.request(
        method='GET',
        path='/v2/pet/findByTags',
        headers=headers,
        params=params,
        )
    assert response.status == 200, 'Response body is : ' + (await response.read()).decode('utf-8')


async def test_get_pet_by_id(client):
    """Test case for get_pet_by_id

    Find pet by ID
    """
    headers = {
        'Accept': 'application/json',
        'api_key': 'special-key',
    }
    response = await client.request(
        method='GET',
        path='/v2/pet/{pet_id}'.format(pet_id=56),
        headers=headers,
        )
    assert response.status == 200, 'Response body is : ' + (await response.read()).decode('utf-8')


async def test_update_pet(client):
    """Test case for update_pet

    Update an existing pet
    """
    body = Pet().to_dict()
    headers = {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
        'Authorization': 'Bearer special-key',
    }
    response = await client.request(
        method='PUT',
        path='/v2/pet',
        headers=headers,
        json=body,
        )
    assert response.status == 200, 'Response body is : ' + (await response.read()).decode('utf-8')


async def test_update_pet_with_form(client):
    """Test case for update_pet_with_form

    Updates a pet in the store with form data
    """
    headers = {
        'Accept': 'application/json',
        'Authorization': 'Bearer special-key',
    }
    data = dict(name='name_example',
                status='status_example')
    response = await client.request(
        method='POST',
        path='/v2/pet/{pet_id}'.format(pet_id=56),
        headers=headers,
        data=data,
        )
    assert response.status == 200, 'Response body is : ' + (await response.read()).decode('utf-8')


async def test_upload_file(client):
    """Test case for upload_file

    uploads an image
    """
    headers = {
        'Accept': 'application/json',
        'Authorization': 'Bearer special-key',
    }
    data = dict(additional_metadata='additional_metadata_example',
                file=(BytesIO(b'some file data'), 'file.txt'))
    response = await client.request(
        method='POST',
        path='/v2/pet/{pet_id}/uploadImage'.format(pet_id=56),
        headers=headers,
        data=data,
        )
    assert response.status == 200, 'Response body is : ' + (await response.read()).decode('utf-8')

