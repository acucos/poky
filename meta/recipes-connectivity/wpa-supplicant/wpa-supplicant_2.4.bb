SUMMARY = "Client for Wi-Fi Protected Access (WPA)"
HOMEPAGE = "http://hostap.epitest.fi/wpa_supplicant/"
BUGTRACKER = "http://hostap.epitest.fi/bugz/"
SECTION = "network"
LICENSE = "BSD"
LIC_FILES_CHKSUM = "file://COPYING;md5=36b27801447e0662ee0138d17fe93880 \
                    file://README;beginline=1;endline=56;md5=7f393579f8b109fe91f3b9765d26c7d3 \
                    file://wpa_supplicant/wpa_supplicant.c;beginline=1;endline=12;md5=3430fda79f2ba1dd545f0b3c4d6e4d24 "
DEPENDS = "dbus libnl libgcrypt"
RRECOMMENDS_${PN} = "wpa-supplicant-passphrase wpa-supplicant-cli"

PACKAGECONFIG ??= "gnutls"
PACKAGECONFIG[gnutls] = ",,gnutls"
PACKAGECONFIG[openssl] = ",,openssl"

inherit systemd

SYSTEMD_SERVICE_${PN} = "wpa_supplicant.service wpa_supplicant-nl80211@.service wpa_supplicant-wired@.service"
SYSTEMD_AUTO_ENABLE = "disable"

SRC_URI = "http://hostap.epitest.fi/releases/wpa_supplicant-${PV}.tar.gz \
           http://snapshot.debian.org/archive/debian/20151115T033945Z/pool/main/w/wpa/wpa_2.3-2.3.debian.tar.xz;name=wpa-debian \
           file://defconfig \
           file://wpa_supplicant.conf \
           file://wpa_supplicant.conf-sane \
           file://99_wpa_supplicant \
           file://0001-AP-WMM-Fix-integer-underflow-in-WMM-Action-frame-par.patch \
           file://0001-P2P-Validate-SSID-element-length-before-copying-it-C.patch \
           file://0001-WPS-Fix-HTTP-chunked-transfer-encoding-parser.patch \
           file://0001-EAP-pwd-peer-Fix-payload-length-validation-for-Commi.patch \
           file://0002-EAP-pwd-server-Fix-payload-length-validation-for-Com.patch \
           file://0003-EAP-pwd-peer-Fix-Total-Length-parsing-for-fragment-r.patch \
           file://0004-EAP-pwd-server-Fix-Total-Length-parsing-for-fragment.patch \
           file://0005-EAP-pwd-peer-Fix-asymmetric-fragmentation-behavior.patch \
          "
SRC_URI[md5sum] = "f0037dbe03897dcaf2ad2722e659095d"
SRC_URI[sha256sum] = "058dc832c096139a059e6df814080f50251a8d313c21b13364c54a1e70109122"

SRC_URI[wpa-debian.md5sum] = "38dedea3056ee103fb70c544ae1b99b8"
SRC_URI[wpa-debian.sha256sum] = "aebdeda11461e93d7f37df5f45643a3fc3c203853c884d983ed4f558604f0b2f"

S = "${WORKDIR}/wpa_supplicant-${PV}"

PACKAGES_prepend = "wpa-supplicant-passphrase wpa-supplicant-cli wpa-supplicant-ifplugd "
FILES_wpa-supplicant-passphrase = "${bindir}/wpa_passphrase"
FILES_wpa-supplicant-cli = "${sbindir}/wpa_cli"
FILES_wpa-supplicant-ifplugd = "${sysconfdir}/ifplugd/action.d/ ${sysconfdir}/wpa_supplicant/action_wpa.sh"
FILES_${PN} += "${datadir}/dbus-1/system-services/*"
CONFFILES_${PN} += "${sysconfdir}/wpa_supplicant.conf"

do_configure () {
	${MAKE} -C wpa_supplicant clean
	install -m 0755 ${WORKDIR}/defconfig wpa_supplicant/.config
	echo "CFLAGS +=\"-I${STAGING_INCDIR}/libnl3\"" >> wpa_supplicant/.config
	echo "DRV_CFLAGS +=\"-I${STAGING_INCDIR}/libnl3\"" >> wpa_supplicant/.config
	
	if echo "${PACKAGECONFIG}" | grep -qw "openssl"; then
        	ssl=openssl
	elif echo "${PACKAGECONFIG}" | grep -qw "gnutls"; then
        	ssl=gnutls
	fi
	if [ -n "$ssl" ]; then
        	sed -i "s/%ssl%/$ssl/" wpa_supplicant/.config
	fi

	# For rebuild
	rm -f wpa_supplicant/*.d wpa_supplicant/dbus/*.d
}

export EXTRA_CFLAGS = "${CFLAGS}"
export BINDIR = "${sbindir}"

do_compile () {
	unset CFLAGS CPPFLAGS CXXFLAGS
	sed -e "s:CFLAGS\ =.*:& \$(EXTRA_CFLAGS):g" -i ${S}/src/lib.rules
	oe_runmake -C wpa_supplicant

        cd "${WORKDIR}/debian"
        sed -i -e 's,"/sbin/wpa,"/usr/sbin/wpa,g' ifupdown/functions.sh
}

do_install () {
	install -d ${D}${sbindir}
	install -m 755 wpa_supplicant/wpa_supplicant         ${D}${sbindir}
	install -m 755 wpa_supplicant/wpa_cli                ${D}${sbindir}
	install -m 755 ${WORKDIR}/debian/ifupdown/wpa_action ${D}${sbindir}

	install -d ${D}${bindir}
	install -m 755 wpa_supplicant/wpa_passphrase ${D}${bindir}

	install -d ${D}${docdir}/wpa_supplicant
	install -m 644 wpa_supplicant/README ${WORKDIR}/wpa_supplicant.conf ${D}${docdir}/wpa_supplicant

	install -d ${D}${sysconfdir}
	install -m 600 ${WORKDIR}/wpa_supplicant.conf-sane ${D}${sysconfdir}/wpa_supplicant.conf

	install -d ${D}${sysconfdir}/wpa_supplicant/
        install -m 755 ${WORKDIR}/debian/ifupdown/action_wpa.sh ${D}${sysconfdir}/wpa_supplicant/
        install -m 644 ${WORKDIR}/debian/ifupdown/functions.sh ${D}${sysconfdir}/wpa_supplicant/
        install -m 755 ${WORKDIR}/debian/ifupdown/wpasupplicant.sh ${D}${sysconfdir}/wpa_supplicant/ifupdown.sh

	for nethook in if-up.d if-pre-up.d if-post-down.d if-down.d; do
            install -d ${D}${sysconfdir}/network/${nethook}/
            (cd ${D}${sysconfdir}/network/ && \
                ln -sf ../../wpa_supplicant/ifupdown.sh ${nethook}/wpa-supplicant)
        done

	install -d ${D}${sysconfdir}/ifplugd/action.d/
        (cd ${D}${sysconfdir}/ifplugd/action.d/ && ln -sf ../../wpa_supplicant/action_wpa.sh action_wpa)

        install -d ${D}${mandir}/man5 ${D}${mandir}/man8
        install -m 0644 wpa_supplicant/doc/docbook/wpa_supplicant.conf.5 ${D}${mandir}/man5/
        install -m 0644 wpa_supplicant/doc/docbook/wpa_passphrase.8 wpa_supplicant/doc/docbook/wpa_supplicant.8 \
            wpa_supplicant/doc/docbook/wpa_cli.8 ${WORKDIR}/debian/ifupdown/wpa_action.8 \
            ${D}${mandir}/man8/

	install -d ${D}/${sysconfdir}/dbus-1/system.d
	install -m 644 wpa_supplicant/dbus/dbus-wpa_supplicant.conf ${D}/${sysconfdir}/dbus-1/system.d
	install -d ${D}/${datadir}/dbus-1/system-services
	install -m 644 wpa_supplicant/dbus/*.service ${D}/${datadir}/dbus-1/system-services

	if ${@bb.utils.contains('DISTRO_FEATURES','systemd','true','false',d)}; then
		install -d ${D}/${systemd_unitdir}/system
		install -m 644 wpa_supplicant/systemd/*.service ${D}/${systemd_unitdir}/system
	fi

	install -d ${D}/etc/default/volatiles
	install -m 0644 ${WORKDIR}/99_wpa_supplicant ${D}/etc/default/volatiles
}

pkg_postinst_wpa-supplicant () {
	# If we're offline, we don't need to do this.
	if [ "x$D" = "x" ]; then
		killall -q -HUP dbus-daemon || true
	fi

}
