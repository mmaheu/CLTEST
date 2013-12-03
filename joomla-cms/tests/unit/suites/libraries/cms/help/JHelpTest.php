<?php
/**
 * @package     Joomla.UnitTest
 * @subpackage  Help
 *
 * @copyright   Copyright (C) 2005 - 2013 Open Source Matters, Inc. All rights reserved.
 * @license     GNU General Public License version 2 or later; see LICENSE
 */

/**
 * Test class for JHelp.
 *
 * @package     Joomla.UnitTest
 * @subpackage  Help
 * @since       3.0
 */
class JHelpTest extends TestCase
{
	/**
	 * The mock config object
	 *
	 * @var    JRegistry
	 * @since  3.0
	 */
	protected $config;

	/**
	 * Sets up the fixture, for example, opens a network connection.
	 * This method is called before a test is executed.
	 *
	 * @return  void
	 *
	 * @since   3.0
	 */
	protected function setUp()
	{
		parent::setUp();

		// Store the factory state so we can mock the necessary objects
		$this->saveFactoryState();

		JFactory::$application = $this->getMockApplication();
		JFactory::$config      = $this->getMockConfig();
		JFactory::$session     = $this->getMockSession();

		// Set up our mock config
		$this->config = JFactory::getConfig();
		$this->config->set('helpurl', 'http://help.joomla.org/proxy/index.php?option=com_help&amp;keyref=Help{major}{minor}:{keyref}');

		// Load the admin en-GB.ini language file
		JFactory::getLanguage()->load('', JPATH_ADMINISTRATOR);
	}

	/**
	 * Tears down the fixture, for example, closes a network connection.
	 * This method is called after a test is executed.
	 *
	 * @return  void
	 *
	 * @since   3.0
	 */
	protected function tearDown()
	{
		// Restore the state
		$this->restoreFactoryState();

		parent::tearDown();
	}

	/**
	 * Tests the createURL method for com_content's Article Manager view
	 *
	 * @return  void
	 *
	 * @since   3.0
	 */
	public function testCreateURL_com_content()
	{
		$this->assertThat(
			JHelp::createURL('JHELP_CONTENT_ARTICLE_MANAGER', false, null, 'com_content'),
			$this->equalTo('help/en-GB/Content_Article_Manager.html'),
			'Creates a local help URL for com_content Article Manager.'
		);
	}

	/**
	 * Tests the createSiteList method with no XML file passed in the params
	 *
	 * @return  void
	 *
	 * @since   3.0
	 */
	public function testCreateSiteList_noXML()
	{
		$this->assertThat(
			JHelp::createSiteList(null),
			$this->isType('array'),
			'Returns the default help site list'
		);
	}

	/**
	 * Tests the createSiteList method with an XML file passed in the params
	 *
	 * @return  void
	 *
	 * @since   3.0
	 */
	public function testCreateSiteList_withXML()
	{
		$this->assertThat(
			JHelp::createSiteList(JPATH_ADMINISTRATOR . '/help/helpsites.xml'),
			$this->isType('array'),
			'Returns the help site list defined in the XML file'
		);
	}
}
