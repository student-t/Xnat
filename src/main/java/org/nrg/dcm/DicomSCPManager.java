/*
 * org.nrg.dcm.DicomSCPManager
 * XNAT http://www.xnat.org
 * Copyright (c) 2014, Washington University School of Medicine
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 *
 * Last modified 7/10/13 9:04 PM
 */
package org.nrg.dcm;

import org.nrg.dcm.preferences.DicomSCPInstance;
import org.nrg.dcm.preferences.DicomSCPPreference;
import org.nrg.framework.exceptions.NrgServiceError;
import org.nrg.framework.exceptions.NrgServiceException;
import org.nrg.framework.exceptions.NrgServiceRuntimeException;
import org.nrg.xdat.preferences.SiteConfigPreferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.*;

public class DicomSCPManager {

    @Autowired
    public DicomSCPManager(final DicomSCPPreference dicomScpPreferences, final SiteConfigPreferences siteConfigPreferences) {
        _dicomScpPreferences = dicomScpPreferences;
        _siteConfigPreferences = siteConfigPreferences;
    }

    @PreDestroy
    public void shutdown() {
        _log.debug("Handling pre-destroy actions, shutting down DICOM SCP receivers.");
        stopDicomSCPs();
    }

    public DicomSCP create(final DicomSCPInstance instance) throws NrgServiceException {
        instance.setId(getNextKey());
        try {
            _dicomScpPreferences.setDicomSCPInstance(instance);
            if (_log.isDebugEnabled()) {
                _log.debug("Created new DICOM SCP: " + instance.toString());
            }
            return _dicomScpPreferences.getDicomSCP(instance.getId());
        } catch (IOException e) {
            throw new NrgServiceRuntimeException(NrgServiceError.Unknown, "Unable to create DICOM SCP: " + instance.getAeTitle() + ":" + instance.getPort(), e);
        }
    }

    public void delete(final int id) throws NrgServiceException {
        if (!_dicomScpPreferences.hasDicomSCPInstance(id)) {
            throw new NrgServiceException(NrgServiceError.UnknownEntity, "There is no DICOM SCP instance with the ID " + id);
        }
        if (_log.isDebugEnabled()) {
            _log.debug("Deleting DICOM SCP: " + id);
        }
        _dicomScpPreferences.deleteDicomSCPInstance(id);
    }

    public List<DicomSCPInstance> getDicomSCPInstances() {
        return new ArrayList<>(_dicomScpPreferences.getDicomSCPInstances().values());
    }

    public void setDicomSCPInstance(final DicomSCPInstance instance) {
        try {
            _dicomScpPreferences.setDicomSCPInstance(instance);
        } catch (IOException e) {
            throw new NrgServiceRuntimeException(NrgServiceError.Unknown, "Unable to update DICOM SCP: " + instance.getAeTitle() + ":" + instance.getPort(), e);
        }
    }

    public List<DicomSCP> startOrStopDicomSCPAsDictatedByConfiguration() {
        final boolean enableDicomReceiver = _siteConfigPreferences.isEnableDicomReceiver();
        return enableDicomReceiver ? startDicomSCPs() : stopDicomSCPs();
    }

    public List<DicomSCP> startDicomSCPs() {
        final List<DicomSCP> started = new ArrayList<>();
        for (final DicomSCPInstance instance : _dicomScpPreferences.getDicomSCPInstances().values()) {
            if (instance.isEnabled()) {
                started.add(startDicomSCP(instance));
            }
        }
        return started;
    }

    public void startDicomSCP(final int id) {
        startDicomSCP(_dicomScpPreferences.getDicomSCPInstance(id));
    }

    public List<DicomSCP> stopDicomSCPs() {
        final List<DicomSCP> stopped = new ArrayList<>();
        for (final DicomSCP dicomSCP : _dicomScpPreferences.getDicomSCPs()) {
            if (dicomSCP.isStarted()) {
                dicomSCP.stop();
                stopped.add(dicomSCP);
            }
        }
        return stopped;
    }

    public void stopDicomSCP(final int id) {
        final DicomSCPInstance instance = _dicomScpPreferences.getDicomSCPInstance(id);
        if (instance == null) {
            throw new NrgServiceRuntimeException(NrgServiceError.UnknownEntity, "Couldn't find the DICOM SCP instance identified by " + id);
        }
        try {
            final DicomSCP dicomSCP = _dicomScpPreferences.getDicomSCP(id);
            if (dicomSCP != null) {
                if (dicomSCP.isStarted()) {
                    dicomSCP.stop();
                }
            }
        } catch (IOException e) {
            throw new NrgServiceRuntimeException(NrgServiceError.Unknown, "Unable to stop DICOM SCP: " + instance.getAeTitle() + ":" + instance.getPort(), e);
        }
    }

    public void enableDicomSCP(final int id) {
        final DicomSCPInstance instance = _dicomScpPreferences.getDicomSCPInstance(id);
        try {
            if (!instance.isEnabled()) {
                instance.setEnabled(true);
                _dicomScpPreferences.setDicomSCPInstance(instance);
            }
            final DicomSCP dicomSCP = _dicomScpPreferences.getDicomSCP(id);
            if (!dicomSCP.isStarted()) {
                dicomSCP.start();
            }
        } catch (IOException e) {
            throw new NrgServiceRuntimeException(NrgServiceError.Unknown, "Unable to enable DICOM SCP: " + instance.getAeTitle() + ":" + instance.getPort(), e);
        }
    }

    public void disableDicomSCP(final int id) {
        final DicomSCPInstance instance = _dicomScpPreferences.getDicomSCPInstance(id);
        try {
            if (instance.isEnabled()) {
                instance.setEnabled(false);
                _dicomScpPreferences.setDicomSCPInstance(instance);
            }
            final DicomSCP dicomSCP = _dicomScpPreferences.getDicomSCP(id);
            if (dicomSCP.isStarted()) {
                dicomSCP.stop();
            }
        } catch (IOException e) {
            throw new NrgServiceRuntimeException(NrgServiceError.Unknown, "Unable to disable DICOM SCP: " + instance.getAeTitle() + ":" + instance.getPort(), e);
        }
    }

    public Map<DicomSCP, Boolean> areDicomSCPsStarted() {
        final Map<DicomSCP, Boolean> statuses = new HashMap<>();
        for (final DicomSCP dicomSCP : _dicomScpPreferences.getDicomSCPs()) {
            statuses.put(dicomSCP, dicomSCP.isStarted());
        }
        return statuses;
    }

    public boolean hasDicomSCP(final int id) {
        return _dicomScpPreferences.hasDicomSCPInstance(id);
    }

    public DicomSCPInstance getDicomSCPInstance(final int id) {
        return _dicomScpPreferences.getDicomSCPInstance(id);
    }

    private DicomSCP startDicomSCP(final DicomSCPInstance instance) {
        try {
            final DicomSCP dicomSCP = _dicomScpPreferences.getDicomSCP(instance.getId());
            dicomSCP.start();
            return dicomSCP;
        } catch (IOException e) {
            throw new NrgServiceRuntimeException(NrgServiceError.Unknown, "Unable to start DICOM SCP: " + instance.getAeTitle() + ":" + instance.getPort(), e);
        }
    }

    private int getNextKey() {
        final Set<String> keys = _dicomScpPreferences.getDicomSCPInstances().keySet();
        final Set<Integer> values = new HashSet<>(keys.size());
        for (final String key : keys) {
            values.add(Integer.parseInt(key));
        }
        return Collections.max(values) + 1;
    }

    private static final Logger _log = LoggerFactory.getLogger(DicomSCPManager.class);

    private final DicomSCPPreference    _dicomScpPreferences;
    private final SiteConfigPreferences _siteConfigPreferences;
}
