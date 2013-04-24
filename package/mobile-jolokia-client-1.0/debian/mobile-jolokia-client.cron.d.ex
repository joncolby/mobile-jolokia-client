#
# Regular cron jobs for the mobile-jolokia-client package
#
0 4	* * *	root	[ -x /usr/bin/mobile-jolokia-client_maintenance ] && /usr/bin/mobile-jolokia-client_maintenance
