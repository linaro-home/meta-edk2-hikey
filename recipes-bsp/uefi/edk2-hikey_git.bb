require edk2_git.bb

COMPATIBLE_MACHINE = "hikey"

DEPENDS_append = " dosfstools-native mtools-native grub optee-os"

SRCREV_edk2_hikey = "465663e9f128428323e6c6e4431dd15ac287a24c"
SRCREV_atf_hikey = "fb1158a365e2bf5bba638cde950678fddf67fe60"
SRCREV_openplatformpkg_hikey = "0fcfe9d6f85dfed70dad391077b79a58363c9f6a"
SRCREV_uefitools_hikey = "abbe1c0a2dbeadde0e3c5f3a183f6c595b70158e"
SRCREV_rdkpkg_hikey = "e63c8172aa0c369972049a272152bb080e881923"
SRC_URI[openssl.md5sum] = "96322138f0b69e61b7212bc53d5e912b"

SRC_URI_hikey = "git://github.com/tianocore/edk2.git;name=edk2 \
	         git://github.com/96boards-hikey/arm-trusted-firmware.git;name=atf;branch=hikey;destsuffix=git/atf \
       		 git://github.com/linaro-home/OpenPlatformPkg.git;name=openplatformpkg;branch=hikey-rdk;destsuffix=git/OpenPlatformPkg \
           	 git://git.linaro.org/uefi/uefi-tools.git;name=uefitools;destsuffix=git/uefi-tools \
	   	 git://github.com/linaro-home/RdkPkg.git;name=rdkpkg;destsuffix=git/RdkPkg \
	   	 https://ftp.openssl.org/source/old/1.0.2/openssl-1.0.2j.tar.gz;name=openssl \
           	 file://grub.cfg.in \
    		 file://0001-edk2-remove-warning.patch \
          	"

# /usr/lib/edk2/bl1.bin not shipped files. [installed-vs-shipped]
INSANE_SKIP_${PN} += "installed-vs-shipped"

OPTEE_OS_ARG = "-s ${EDK2_DIR}/optee_os"

# We need the secure payload (Trusted OS) built from OP-TEE Trusted OS (tee.bin)
# but we have already built tee.bin from optee-os recipe and
# uefi-build.sh script has a few assumptions...
# Copy tee.bin and create dummy files to make uefi-build.sh script happy
do_compile_prepend() {
    install -D -p -m0644 \
      ${STAGING_DIR_HOST}/lib/firmware/tee.bin \
      ${EDK2_DIR}/optee_os/out/arm-plat-hikey/core/tee.bin

    mkdir -p ${EDK2_DIR}/optee_os/documentation
    touch ${EDK2_DIR}/optee_os/documentation/optee_design.md

    printf "all:\n"  > ${EDK2_DIR}/optee_os/Makefile
    printf "\ttrue" >> ${EDK2_DIR}/optee_os/Makefile

    cp -r ${EDK2_DIR}/../openssl-1.0.2j ${EDK2_DIR}/CryptoPkg/Library/OpensslLib
}

do_install() {
    install -D -p -m0644 ${EDK2_DIR}/atf/build/${UEFIMACHINE}/release/bl1.bin ${D}${libdir}/edk2/bl1.bin

    # Install grub configuration
    sed -e "s|@DISTRO|${DISTRO}|" \
        -e "s|@KERNEL_IMAGETYPE|${KERNEL_IMAGETYPE}|" \
        -e "s|@CMDLINE|${CMDLINE}|" \
        < ${WORKDIR}/grub.cfg.in \
        > ${WORKDIR}/grub.cfg
    install -D -p -m0644 ${WORKDIR}/grub.cfg ${D}/boot/grub/grub.cfg
}

# Create a 64M boot image. block size is 1024. (64*1024=65536)
BOOT_IMAGE_SIZE = "65536"
BOOT_IMAGE_BASE_NAME = "boot-${PKGV}-${PKGR}-${MACHINE}-${DATETIME}"
BOOT_IMAGE_BASE_NAME[vardepsexclude] = "DATETIME"

# HiKey boot image requires fastboot and grub EFI
# ensure we deploy grubaa64.efi before we try to create the boot image.
do_deploy[depends] += "grub:do_deploy"
do_deploy_append() {
    # Ship nvme.img with UEFI binaries for convenience
    dd if=/dev/zero of=${DEPLOYDIR}/nvme.img bs=128 count=1024

    # Create boot image
    mkfs.vfat -F32 -n "boot" -C ${DEPLOYDIR}/${BOOT_IMAGE_BASE_NAME}.uefi.img ${BOOT_IMAGE_SIZE}
    mmd -i ${DEPLOYDIR}/${BOOT_IMAGE_BASE_NAME}.uefi.img ::EFI
    mmd -i ${DEPLOYDIR}/${BOOT_IMAGE_BASE_NAME}.uefi.img ::EFI/BOOT
    chmod 644 ${DEPLOYDIR}/${BOOT_IMAGE_BASE_NAME}.uefi.img

    (cd ${DEPLOYDIR} && ln -sf ${BOOT_IMAGE_BASE_NAME}.uefi.img boot-${MACHINE}.uefi.img)
}

