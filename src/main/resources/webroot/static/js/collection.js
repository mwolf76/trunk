function setup_delete_resource_modal() {
    $('#delete-resource-confirm')
        .on('click', function (event) {
            deleteResource('#', function() {
                window.location.replace("..");
            });
        });
}

function setup_add_resource_modal() {
    /* selected a file enables commit button */
    $('#additional-resource-file-selector')
        .on('change', function (event) {
            var filename = this.files[0].name;

            $('#add-resource-file-label')
                .addClass('hidden');

            $('#commit-added-resource-file-label')
                .removeClass('hidden');

            $('#additional-resource-filename-display')
                .html(filename);
        });

    /* add collections */
    $('#commit-added-collection-resource')
        .on('click', function (event) {
            var resourceName = $('#additional-collection-resource-input').val();
            putResource(resourceName, function() {
                window.location.reload(true);
            });
        });

    /* add documents (one file uploads only) */
    $('#commit-added-document-resource')
        .on('click', function (event) {
            var file = $('#additional-resource-file-selector')[0].files[0];
            postResource(file, file.name, function () {
                window.location.reload(true);
            });
        });
} /* add_update_resource_modal() */

function setup_share_resource_modal() {
    /* share collections */
    $('#commit-shared-collection-resource')
        .on('click', function (event) {
            var collectionPath = $('#share-collection-resource-path').val();
            var authorizedEmails = $('#share-collection-resource-input').val().split(';').map(function (s) {
                return ( s || '' ).replace( /^\s+|\s+$/g, '' );
            });

            putShare(collectionPath, authorizedEmails, function() {
                window.location.reload(true);
            });
        });
} /* share_update_resource_modal() */

function setup_update_resource_modal() {
    $('#update-resource-modal')
        .on('show.bs.modal', function (event) {
            var relatedTarget = $(event.relatedTarget)[0];
            var json = relatedTarget.dataset.metadata;
            var data = JSON.parse(json);
            var url = relatedTarget.dataset.url;
            var modal = $(this);

            /* populate modal dialog fields */
            modal.find('#roURL').val(url);
            modal.find('#roName').val(data.name);
            modal.find('#roMimeType').val(data.mimeType);
            modal.find('#roCreated').val(data.created);
            modal.find('#roModified').val(data.modified);
            modal.find('#roAccessed').val(data.accessed);
            modal.find('#roLength').val(data.length);
        })
        .on('hide.bs.modal', function (event) {
            $('#update-resource-file-label')
                .removeClass('hidden');

            $('#commit-updated-resource-file-label')
                .addClass('hidden');

            $('#update-resource-file-display')
                .html("");
        });

    /* selected a file enables commit button */
    $('#updated-resource-file-selector')
        .on('change', function (event) {
            var filename = this.files[0].name;

            $('#update-resource-file-label')
                .addClass('hidden');

            $('#commit-updated-resource-file-label')
                .removeClass('hidden');

            $('#update-resource-file-display')
                .html(filename);
        });

    /* one file upload only */
    $('#commit-updated-resource-file-button')
        .on('click', function (event) {
            var file = $('#updated-resource-file-selector')[0].files[0];
            var filename = $('#roURL').val(); /* retrieve original URL from hidden */
            postResource(file, filename, function() {
                window.location.reload(true);
            });
        });

    /* user confirmed deletion for this document resource */
    $('#update-resource-delete-button')
        .click(function () {
            var toDelete = $('#roURL').val();
            deleteResource(toDelete, function() {
                window.location.reload(true);
            });
        });
} /* setup_update_resource_modal() */

/*** ajax helpers ***/
function defaultErrorCallback(jqXHR, textStatus, errorThrown) {
    console.warn(jqXHR.responseText);
}

function deleteResource(resource, successCallback, errorCallback) {
    console.log('Deleting resource: ' + resource);
    $.ajax({
        type: "DELETE",
        url: resource,
        success: successCallback,
        error: errorCallback || defaultErrorCallback
    });
}

function downloadResource(resource, successCallback, errorCallback) {
    var full = resource + "?tarball=t";
    $.ajax({
        type: "GET",
        url: full,
        success: successCallback,
        error: errorCallback || defaultErrorCallback
    });
}

/* documents only */
function postResource(resource, resourceName, successCallback, errorCallback) {
    console.log('Uploading document: ' + resource);
    $.ajax({
        type: 'POST',
        url: resourceName,
        contentType: "multipart/form-data",
        processData: false,
        data: resource,
        success: successCallback,
        error: errorCallback || defaultErrorCallback
    });
}

/* collections only */
function putResource(resource, successCallback, errorCallback) {
    console.log('Creating collection: ', resource);
    $.ajax({
        type: "PUT",
        url: resource,
        success: successCallback,
        error: errorCallback || defaultErrorCallback
    });
}

/* shares mgmt */
function putShare(collectionPath, authorizedUsers, successCallback, errorCallback) {
    console.log("Putting sharing info for collection path: ", collectionPath);
    console.log("Authorized users: " + authorizedUsers);

    $.ajax({
        type: "PUT",
        url: "/share/" + collectionPath,
        data: JSON.stringify({
            authorizedUsers: authorizedUsers
        }),
        success: successCallback,
        error: errorCallback || defaultErrorCallback
    });
}

/* setting up */
$(document).ready(function() {
    setup_add_resource_modal();
    setup_share_resource_modal();
    setup_update_resource_modal();
    setup_delete_resource_modal();
});
