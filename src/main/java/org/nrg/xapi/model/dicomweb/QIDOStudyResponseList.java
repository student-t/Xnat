package org.nrg.xapi.model.dicomweb;

import java.util.*;

public class QIDOStudyResponseList extends ArrayList<QIDOResponse> {

    public boolean add(QIDOResponse newResponse) {
        if( this.isEmpty()) {
            return super.add( newResponse);
        }
        else {
            for (QIDOResponse response : this) {
                if ( isSameStudy( response, newResponse)) {
                    return merge(response, newResponse);
                }
            }
            return super.add(newResponse);
        }
    }

    private boolean isSameStudy(QIDOResponse response, QIDOResponse newResponse) {
        String suid = response.getStudyInstanceUID();
        String newSuid = newResponse.getStudyInstanceUID();
        return suid.equals( newSuid);
    }

    private boolean merge(QIDOResponse response, QIDOResponse newResponse) {
        boolean b;

        b = mergeModalitiesInStudy( response, newResponse);
        mergeSeriesCount( response, newResponse);
        mergeInstanceCount( response, newResponse);
        return b;
    }

    private boolean mergeModalitiesInStudy(QIDOResponse response, QIDOResponse newResponse) {
        String[] modalities = parseMultiValues( response.getModalitiesInStudy());
        String[] newModalities = parseMultiValues( newResponse.getModalitiesInStudy());
        Set<String> set = new HashSet<>();
        set.addAll( Arrays.asList( modalities));
        set.addAll( Arrays.asList( newModalities));
        StringBuilder sb = new StringBuilder();
        Iterator<String> it = set.iterator();
        while( it.hasNext()) {
            sb.append( it.next());
            if( it.hasNext()) sb.append("\\");
        }

        response.setModalitiesInStudy( sb.toString());
        return true;
    }

    private int mergeSeriesCount(QIDOResponse response, QIDOResponse newResponse) {
        int count = response.getNumberOfStudyRelatedSeries() + newResponse.getNumberOfStudyRelatedSeries();
        response.setNumberOfStudyRelatedSeries( count);
        return count;
    }

    private int mergeInstanceCount(QIDOResponse response, QIDOResponse newResponse) {
        int count = response.getNumberOfStudyRelatedInstances() + newResponse.getNumberOfStudyRelatedInstances();
        response.setNumberOfStudyRelatedInstances( count);
        return count;
    }

    private String[] parseMultiValues(String modalitiesInStudy) {
        return modalitiesInStudy.split("\\\\");
    }
}
