<?php
/**
 * @package     Joomla.UnitTest
 * @subpackage  Plugin
 *
 * @copyright   Copyright (C) 2005 - 2013 Open Source Matters, Inc. All rights reserved.
 * @license     GNU General Public License version 2 or later; see LICENSE
 */

/**
 * Stub plugin class for unit testing
 *
 * @package     Joomla.UnitTest
 * @subpackage  Plugin
 * @since       3.1
 */
class PlgSystemBase extends JPlugin
{
	/**
	 * Constructor
	 *
	 * @since   3.1
	 */
	public function __construct()
	{
		$this->autoloadLanguage = true;

		// Config array for JPlugin constructor
		$config = array();
		$config['name']   = 'Base';
		$config['type']   = 'System';
		$config['params'] = new JRegistry;

		$dispatcher = JEventDispatcher::getInstance();

		// Call the parent constructor
		parent::__construct($dispatcher, $config);
	}
}
