/*
 * web: org.nrg.xapi.rest.data.CatalogApi
 * XNAT http://www.xnat.org
 * Copyright (c) 2005-2017, Washington University School of Medicine and Howard Hughes Medical Institute
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

package org.nrg.xapi.rest.data;

import com.google.common.base.Joiner;
import io.swagger.annotations.*;
import org.apache.commons.collections.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.nrg.action.ClientException;
import org.nrg.action.ServerException;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.framework.exceptions.NrgServiceError;
import org.nrg.framework.exceptions.NrgServiceException;
import org.nrg.framework.exceptions.NrgServiceRuntimeException;
import org.nrg.xapi.exceptions.InsufficientPrivilegesException;
import org.nrg.xapi.exceptions.NoContentException;
import org.nrg.xapi.exceptions.NotFoundException;
import org.nrg.xapi.rest.AbstractXapiRestController;
import org.nrg.xapi.rest.ProjectId;
import org.nrg.xapi.rest.XapiRequestMapping;
import org.nrg.xdat.bean.CatCatalogBean;
import org.nrg.xdat.model.CatCatalogI;
import org.nrg.xdat.preferences.SiteConfigPreferences;
import org.nrg.xdat.security.helpers.Users;
import org.nrg.xdat.security.services.RoleHolder;
import org.nrg.xdat.security.services.UserManagementServiceI;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.services.archive.CatalogService;
import org.nrg.xnat.web.http.ZipStreamingResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Map;

import static org.nrg.xdat.security.helpers.AccessLevel.Read;

@Api(description = "XNAT Archive and Resource Management API")
@XapiRestController
@RequestMapping(value = "/archive")
public class CatalogApi extends AbstractXapiRestController {
    @Autowired
    public CatalogApi(final UserManagementServiceI userManagementService, final RoleHolder roleHolder, final CatalogService service, final SiteConfigPreferences preferences) {
        super(userManagementService, roleHolder);
        _service = service;
        _preferences = preferences;
    }

    @ApiOperation(value = "Refresh the catalog entry for one or more resources.", notes = "The resource should be identified by standard archive-relative paths, such as /archive/experiments/XNAT_E0001 or /archive/projects/XNAT_01/subjects/XNAT_01_01.", response = Void.class)
    @ApiResponses({@ApiResponse(code = 200, message = "The refresh operation(s) were completed successfully."),
                   @ApiResponse(code = 500, message = "An unexpected or unknown error occurred")})
    @XapiRequestMapping(value = "catalogs/refresh", consumes = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.PUT)
    @ResponseBody
    public ResponseEntity<Void> refreshResourceCatalog(@ApiParam("The list of resources to be refreshed.") @RequestBody final List<String> resources) throws ServerException, ClientException {
        return refreshResourceCatalogWithOptions(null, resources);
    }

    @ApiOperation(value = "Refresh the catalog entry for one or more resources, performing only the operations specified.", notes = "The resource should be identified by standard archive-relative paths, such as /archive/experiments/XNAT_E0001 or /archive/projects/XNAT_01/subjects/XNAT_01_01. The available operations are All, Append, Checksum, Delete, and PopulateStats. They should be comma separated but without spaces. Omitting the operations implies All.", response = Void.class)
    @ApiResponses({@ApiResponse(code = 200, message = "The refresh operation(s) were completed successfully."),
                   @ApiResponse(code = 500, message = "An unexpected or unknown error occurred")})
    @XapiRequestMapping(value = "catalogs/refresh/{operations}", consumes = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.PUT)
    @ResponseBody
    public ResponseEntity<Void> refreshResourceCatalogWithOptions(
            @ApiParam("The operations to be performed") @PathVariable final List<CatalogService.Operation> operations,
            @ApiParam("The list of resources to be refreshed.") @RequestBody final List<String> resources) throws ServerException, ClientException {
        final UserI user = getSessionUser();

        _log.info("User {} requested catalog refresh for the following resources: " + Joiner.on(", ").join(resources));
        if (operations == null) {
            _service.refreshResourceCatalogs(user, resources);
        } else {
            _service.refreshResourceCatalogs(user, resources, operations);
        }

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @ApiOperation(value = "Creates a download catalog for the submitted sessions and other data objects.",
                  notes = "The map submitted to this call supports lists of object IDs organized by key type: sessions, "
                          + "scan_type, scan_format, recon, assessors, and resources. The response for this method is "
                          + "the ID for the catalog of resolved resources, which can be submitted to the download/{catalog} "
                          + "function to retrieve the catalog or to the download/{catalog}/zip function to retrieve the"
                          + "files in the catalog as a zip archive.",
                  response = String.class)
    @ApiResponses({@ApiResponse(code = 200, message = "The download catalog was successfully built."),
                   @ApiResponse(code = 204, message = "No resources were specified."),
                   @ApiResponse(code = 400, message = "Something is wrong with the request format."),
                   @ApiResponse(code = 403, message = "The user is not authorized to access one or more of the specified resources."),
                   @ApiResponse(code = 404, message = "The request was valid but one or more of the specified resources was not found."),
                   @ApiResponse(code = 500, message = "An unexpected or unknown error occurred")})
    @XapiRequestMapping(value = "download", restrictTo = Read, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_XML_VALUE, method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<String> createDownloadSessionsCatalog(@ApiParam("The resources to be cataloged.") @RequestBody @ProjectId final Map<String, List<String>> resources) throws InsufficientPrivilegesException, NoContentException {
        UserI user = getSessionUser();
        if(user==null){
            try{
                user=Users.getGuest();
            }catch(Exception e){
                _log.error("Cannot create download catalog for null user.",e);
            }
        }

        final boolean hasProjectId = resources.containsKey("projectId");
        final boolean hasProjectIds = resources.containsKey("projectIds");
        if (resources.size() == 0 || !resources.containsKey("sessions") || (!hasProjectId && !hasProjectIds)) {
            throw new NoContentException("There were no resources or sessions specified in the request.");
        }

        if (_log.isInfoEnabled()) {
            // You don't normally need to check is isInfoEnabled(), but the Joiner and map testing is somewhat complex,
            // so this removes that unnecessary operation when the logging level is higher than info.
            _log.info("User {} requested download catalog for {} resources in projects {}",
                      user.getUsername(),
                      resources.get("sessions"),
                      Joiner.on(", ").join(hasProjectId && hasProjectIds
                                           ? ListUtils.union(resources.get("projectId"), resources.get("projectIds"))
                                           : (hasProjectId ? resources.get("projectId") : resources.get("projectIds"))));
        }
        return new ResponseEntity<>(_service.buildCatalogForResources(user, resources), HttpStatus.OK);
    }

    @ApiOperation(value = "Retrieves the download catalog for the submitted catalog ID.",
                  notes = "This retrieves a catalog created earlier by the catalog service.",
                  response = CatCatalogBean.class)
    @ApiResponses({@ApiResponse(code = 200, message = "The download catalog was successfully built."),
                   @ApiResponse(code = 204, message = "No resources were specified."),
                   @ApiResponse(code = 400, message = "Something is wrong with the request format."),
                   @ApiResponse(code = 403, message = "The user is not authorized to access one or more of the specified resources."),
                   @ApiResponse(code = 404, message = "The request was valid but one or more of the specified resources was not found."),
                   @ApiResponse(code = 500, message = "An unexpected or unknown error occurred")})
    @XapiRequestMapping(value = "download/{catalogId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_XML_VALUE, method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<CatCatalogI> getDownloadSessionsCatalog(@ApiParam("The ID of the catalog to be downloaded.") @PathVariable final String catalogId) throws InsufficientPrivilegesException, NoContentException, NotFoundException {
        UserI user = getSessionUser();
        if(user==null){
            try{
                user=Users.getGuest();
            }catch(Exception e){
                _log.error("Cannot build catalog for null user.",e);
            }
        }

        _log.info("User {} requested download catalog {}", user.getUsername(), catalogId);
        final CatCatalogI catalog = _service.getCachedCatalog(user, catalogId);
        if (catalog == null) {
            throw new NotFoundException("No catalog with ID " + catalogId + " was found.");
        }
        return new ResponseEntity<>(catalog, HttpStatus.OK);
    }

    @ApiOperation(value = "Downloads the specified catalog as an XML file.", response = StreamingResponseBody.class)
    @ApiResponses({@ApiResponse(code = 200, message = "The requested catalog was successfully downloaded."),
                   @ApiResponse(code = 204, message = "No catalog was specified."),
                   @ApiResponse(code = 400, message = "Something is wrong with the request format."),
                   @ApiResponse(code = 403, message = "The user is not authorized to access the specified catalog."),
                   @ApiResponse(code = 404, message = "The request was valid but the specified catalog was not found."),
                   @ApiResponse(code = 500, message = "An unexpected or unknown error occurred")})
    @XapiRequestMapping(value = "download/{catalogId}/xml", produces = MediaType.APPLICATION_XML_VALUE, method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<StreamingResponseBody> downloadSessionCatalogXml(@ApiParam("The ID of the catalog to be downloaded.") @PathVariable final String catalogId) throws InsufficientPrivilegesException, NoContentException, NotFoundException, IOException, NrgServiceException {
        UserI user = getSessionUser();
        if(user==null){
            try{
                user=Users.getGuest();
            }catch(Exception e){
                _log.error("Cannot build catalog for null user.",e);
            }
        }

        _log.info("User {} requested download catalog: {}", catalogId);
        final CatCatalogI catalog = _service.getCachedCatalog(user, catalogId);
        if (catalog == null) {
            throw new NotFoundException("No catalog with ID " + catalogId + " was found.");
        }
        try {
            return ResponseEntity.ok()
                                 .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
                                 .header(HttpHeaders.CONTENT_DISPOSITION, getAttachmentDisposition(catalogId, "xml"))
                                 .header(HttpHeaders.CONTENT_LENGTH, Long.toString(_service.getCatalogSize(user, catalogId)))
                                 .body((StreamingResponseBody) new StreamingResponseBody() {
                                     @Override
                                     public void writeTo(final OutputStream outputStream) throws IOException {
                                         try (final OutputStreamWriter writer = new OutputStreamWriter(outputStream)) {
                                             if (catalog instanceof CatCatalogBean) {
                                                 ((CatCatalogBean) catalog).toXML(writer, true);
                                             } else {
                                                 try {
                                                     catalog.toXML(writer);
                                                 } catch (Exception e) {
                                                     throw new NrgServiceRuntimeException(NrgServiceError.Unknown, "An error occurred trying to write the catalog " + catalogId + ".", e);
                                                 }
                                             }
                                         }
                                     }
                                 });
        } catch (NrgServiceRuntimeException e) {
            if (e.getCause() != null) {
                throw new NrgServiceException(e.getServiceError(), e.getMessage(), e.getCause());
            }
            throw new NrgServiceException(e.getServiceError(), e.getMessage());
        }
    }

    @ApiOperation(value = "Downloads the contents of the specified catalog as a zip archive.",
                  response = StreamingResponseBody.class)
    @ApiResponses({@ApiResponse(code = 200, message = "The requested resources were successfully downloaded."),
                   @ApiResponse(code = 204, message = "No resources were specified."),
                   @ApiResponse(code = 400, message = "Something is wrong with the request format."),
                   @ApiResponse(code = 403, message = "The user is not authorized to access one or more of the specified resources."),
                   @ApiResponse(code = 404, message = "The request was valid but one or more of the specified resources was not found."),
                   @ApiResponse(code = 500, message = "An unexpected or unknown error occurred")})
    @XapiRequestMapping(value = "download/{catalogId}/zip", produces = ZipStreamingResponseBody.MEDIA_TYPE, method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<StreamingResponseBody> downloadSessionCatalogZip(@ApiParam("The ID of the catalog of resources to be downloaded.") @PathVariable final String catalogId) throws InsufficientPrivilegesException, NoContentException, IOException {
        UserI user = getSessionUser();
        if(user==null){
            try{
                user=Users.getGuest();
            }catch(Exception e){
                _log.error("Cannot build catalog for null user.",e);
            }
        }
        if (StringUtils.isBlank(catalogId)) {
            throw new NoContentException("There was no catalog specified in the request.");
        }
        // TODO: Need to validate the catalog exists (404 if not), the user has permissions to view all resources (403 if not), if that's cool then proceed.
        _log.info("User {} requested download of the catalog {}", user.getLogin(), catalogId);

        return ResponseEntity.ok()
                             .header(HttpHeaders.CONTENT_TYPE, ZipStreamingResponseBody.MEDIA_TYPE)
                             .header(HttpHeaders.CONTENT_DISPOSITION, getAttachmentDisposition(catalogId, "zip"))
                             .body((StreamingResponseBody) new ZipStreamingResponseBody(user, _service.getCachedCatalog(user, catalogId), _preferences.getArchivePath()));
    }

    @ApiOperation(value = "Downloads the specified catalog as a zip archive, using a small empty file for each entry.",
                  response = StreamingResponseBody.class)
    @ApiResponses({@ApiResponse(code = 200, message = "The requested resources were successfully downloaded."),
                   @ApiResponse(code = 204, message = "No resources were specified."),
                   @ApiResponse(code = 400, message = "Something is wrong with the request format."),
                   @ApiResponse(code = 403, message = "The user is not authorized to access one or more of the specified resources."),
                   @ApiResponse(code = 404, message = "The request was valid but one or more of the specified resources was not found."),
                   @ApiResponse(code = 500, message = "An unexpected or unknown error occurred")})
    @XapiRequestMapping(value = "download/{catalogId}/test", produces = ZipStreamingResponseBody.MEDIA_TYPE, method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<StreamingResponseBody> downloadSessionCatalogZipTest(@ApiParam("The ID of the catalog of resources to be downloaded.") @PathVariable final String catalogId) throws InsufficientPrivilegesException, NoContentException, IOException {
        UserI user = getSessionUser();
        if(user==null){
            try{
                user=Users.getGuest();
            }catch(Exception e){
                _log.error("Cannot build catalog for null user.",e);
            }
        }

        if (StringUtils.isBlank(catalogId)) {
            throw new NoContentException("There was no catalog specified in the request.");
        }
        // TODO: Need to validate the catalog exists (404 if not), the user has permissions to view all resources (403 if not), if that's cool then proceed.
        _log.info("User {} requested download of the catalog {}", user.getLogin(), catalogId);

        final CatCatalogI catalog = _service.getCachedCatalog(user, catalogId);
        if (catalog == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        return ResponseEntity.ok()
                             .header(HttpHeaders.CONTENT_TYPE, ZipStreamingResponseBody.MEDIA_TYPE)
                             .header(HttpHeaders.CONTENT_DISPOSITION, getAttachmentDisposition(catalogId, "zip"))
                             .body((StreamingResponseBody) new ZipStreamingResponseBody(user, catalog, _preferences.getArchivePath(), true));
    }

    private static String getAttachmentDisposition(final String name, final String extension) {
        return String.format(ATTACHMENT_DISPOSITION, name, extension);
    }

    private static final String ATTACHMENT_DISPOSITION = "attachment; filename=\"%s.%s\"";

    private static final Logger _log = LoggerFactory.getLogger(CatalogApi.class);

    private final CatalogService        _service;
    private final SiteConfigPreferences _preferences;
}
