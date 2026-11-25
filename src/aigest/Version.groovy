package aigest

import groovy.transform.CompileStatic

@CompileStatic
final class Version {

    public static final String NUMBER = '0.1.3.2'
    public static final String NAME = 'aigest'

    private Version() { }

    static String banner() {
        "${NAME} v${NUMBER}"
    }

}
